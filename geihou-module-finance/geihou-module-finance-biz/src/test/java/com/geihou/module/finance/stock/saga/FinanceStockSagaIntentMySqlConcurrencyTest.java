package com.geihou.module.finance.stock.saga;

import com.geihou.framework.tenant.core.context.TenantContextHolder;
import com.geihou.module.finance.api.cart.enums.CartEventTypeEnum;
import com.geihou.module.finance.checkout.framework.CheckoutStatusEnum;
import com.geihou.module.finance.stock.saga.dal.dataobject.FinanceStockSagaIntentDO;
import com.geihou.module.finance.stock.saga.dal.mapper.FinanceStockSagaIntentMapper;
import com.geihou.module.finance.stock.saga.enums.FinanceStockSagaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Real MySQL 8 regression for duplicate-key reconciliation under
 * REPEATABLE_READ.
 *
 * <p>Both loser transactions first establish an empty snapshot, then block
 * behind an uncommitted winner INSERT. After the winner commits, both losers
 * retain shared duplicate-record locks. They must reconcile through the
 * mapper's {@code FOR SHARE} current read. A {@code FOR UPDATE} lock upgrade
 * deterministically deadlocks one loser in this schedule.
 */
@Testcontainers
@SpringBootTest(
        classes = FinanceStockCommandTestConfig.class,
        properties = {
                "spring.flyway.enabled=false",
                "spring.main.web-application-type=none"
        })
class FinanceStockSagaIntentMySqlConcurrencyTest {

    private static final long TENANT_ID = 1L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("finance_stock_saga_intent")
            .withUsername("geihou")
            .withPassword("geihou");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired private DataSource dataSource;
    @Autowired private FinanceStockSagaIntentMapper actualMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUpSchema() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS finance_stock_saga_intent");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V02_068__finance_stock_saga_intent.sql"));
        }
    }

    @Test
    void createOrGet_multipleDuplicateLosers_useSharedCurrentReadWithoutDeadlock()
            throws Exception {
        CountDownLatch initialReads = new CountDownLatch(2);
        CountDownLatch releaseInitialReads = new CountDownLatch(1);
        CountDownLatch winnerInserted = new CountDownLatch(1);
        CountDownLatch releaseWinnerCommit = new CountDownLatch(1);
        CountDownLatch loserInsertsEntered = new CountDownLatch(2);
        CountDownLatch sharedReadsReady = new CountDownLatch(2);
        CountDownLatch releaseSharedReads = new CountDownLatch(1);

        FinanceStockSagaIntentMapper coordinatingMapper =
                mock(FinanceStockSagaIntentMapper.class, delegatesTo(actualMapper));
        doAnswer(invocation -> {
            FinanceStockSagaIntentDO result = actualMapper.selectByIdentity(
                    invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2));
            initialReads.countDown();
            await(releaseInitialReads, "initial loser reads were not released");
            return result;
        }).when(coordinatingMapper).selectByIdentity(anyLong(), anyString(), anyLong());
        doAnswer(invocation -> {
            loserInsertsEntered.countDown();
            FinanceStockSagaIntentDO intent = invocation.getArgument(0);
            return actualMapper.insert(intent);
        }).when(coordinatingMapper).insert(any(FinanceStockSagaIntentDO.class));
        doAnswer(invocation -> {
            sharedReadsReady.countDown();
            await(releaseSharedReads, "shared current reads were not released");
            return actualMapper.selectByIdentityForShare(
                    invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2));
        }).when(coordinatingMapper).selectByIdentityForShare(
                anyLong(), anyString(), anyLong());

        FinanceStockSagaIntentStore loserStore =
                new FinanceStockSagaIntentStoreImpl(coordinatingMapper);
        FinanceStockSagaIntentStore winnerStore =
                new FinanceStockSagaIntentStoreImpl(actualMapper);
        FinanceStockSagaIntentCreate create = create();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<FinanceStockSagaIntentDO> loser1 = executor.submit(
                    () -> inTenantTransaction(() -> loserStore.createOrGet(create)));
            Future<FinanceStockSagaIntentDO> loser2 = executor.submit(
                    () -> inTenantTransaction(() -> loserStore.createOrGet(create)));
            assertThat(initialReads.await(10, TimeUnit.SECONDS))
                    .as("both losers establish an empty RR snapshot").isTrue();

            Future<FinanceStockSagaIntentDO> winner = executor.submit(() ->
                    inTenantTransaction(() -> {
                        FinanceStockSagaIntentDO intent = winnerStore.createOrGet(create);
                        winnerInserted.countDown();
                        await(releaseWinnerCommit, "winner commit was not released");
                        return intent;
                    }));
            assertThat(winnerInserted.await(10, TimeUnit.SECONDS))
                    .as("winner INSERT is uncommitted").isTrue();

            releaseInitialReads.countDown();
            assertThat(loserInsertsEntered.await(10, TimeUnit.SECONDS))
                    .as("both loser INSERTs are waiting behind the winner").isTrue();
            Thread.sleep(250);
            releaseWinnerCommit.countDown();
            FinanceStockSagaIntentDO winnerIntent = winner.get(10, TimeUnit.SECONDS);

            assertThat(sharedReadsReady.await(10, TimeUnit.SECONDS))
                    .as("both duplicate losers reach the shared current-read barrier").isTrue();
            releaseSharedReads.countDown();
            FinanceStockSagaIntentDO loserIntent1 = loser1.get(10, TimeUnit.SECONDS);
            FinanceStockSagaIntentDO loserIntent2 = loser2.get(10, TimeUnit.SECONDS);

            assertThat(loserIntent1.getId()).isEqualTo(winnerIntent.getId());
            assertThat(loserIntent2.getId()).isEqualTo(winnerIntent.getId());
            assertThat(new JdbcTemplate(dataSource).queryForObject(
                    "SELECT COUNT(*) FROM finance_stock_saga_intent", Integer.class))
                    .isEqualTo(1);
        } finally {
            releaseInitialReads.countDown();
            releaseWinnerCommit.countDown();
            releaseSharedReads.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            TenantContextHolder.clear();
        }
    }

    private FinanceStockSagaIntentCreate create() {
        return new FinanceStockSagaIntentCreate(
                TENANT_ID,
                FinanceStockSagaType.CHECKOUT,
                100L,
                300L,
                9001L,
                "CUSTOMER",
                CheckoutStatusEnum.INITIATED.getCode(),
                CheckoutStatusEnum.ABANDONED.getCode(),
                CartEventTypeEnum.CHECKOUT_ABANDONED.getCode());
    }

    private <T> T inTenantTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(20);
        return transaction.execute(status -> {
            TenantContextHolder.clear();
            TenantContextHolder.setTenantId(TENANT_ID);
            try {
                return action.get();
            } finally {
                TenantContextHolder.clear();
            }
        });
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, e);
        }
    }
}
