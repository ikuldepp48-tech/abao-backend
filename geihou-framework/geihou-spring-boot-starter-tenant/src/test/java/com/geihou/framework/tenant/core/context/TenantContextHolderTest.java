package com.geihou.framework.tenant.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextHolderTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRoundTripSetAndGet() {
        TenantContextHolder.setTenantId(100L);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(100L);
    }

    @Test
    void shouldReturnNullAfterClear() {
        TenantContextHolder.setTenantId(200L);

        TenantContextHolder.clear();

        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void shouldDefaultIgnoreToFalse() {
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldSetAndGetIgnoreTrue() {
        TenantContextHolder.setIgnore(true);

        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    @Test
    void shouldSetAndGetIgnoreFalse() {
        TenantContextHolder.setIgnore(true);

        TenantContextHolder.setIgnore(false);

        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldClearTenantIdAndIgnoreFlag() {
        TenantContextHolder.setTenantId(400L);
        TenantContextHolder.setIgnore(true);

        TenantContextHolder.clear();

        assertThat(TenantContextHolder.getTenantId()).isNull();
        assertThat(TenantContextHolder.isIgnore()).isFalse();
    }

    @Test
    void shouldKeepTenantIdWhenSettingIgnoreFlag() {
        TenantContextHolder.setTenantId(500L);

        TenantContextHolder.setIgnore(true);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(500L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    @Test
    void shouldInheritTenantIdToChildThread() throws InterruptedException {
        TenantContextHolder.setTenantId(300L);
        AtomicReference<Long> otherThreadTenantId = new AtomicReference<>();

        Thread otherThread = new Thread(() -> otherThreadTenantId.set(TenantContextHolder.getTenantId()));
        otherThread.start();
        otherThread.join();

        assertThat(otherThreadTenantId).hasValue(300L);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(300L);
    }

    @Test
    void shouldInheritIgnoreFlagToChildThread() throws InterruptedException {
        TenantContextHolder.setIgnore(true);
        AtomicReference<Boolean> otherThreadIgnore = new AtomicReference<>();

        Thread otherThread = new Thread(() -> otherThreadIgnore.set(TenantContextHolder.isIgnore()));
        otherThread.start();
        otherThread.join();

        assertThat(otherThreadIgnore).hasValue(true);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    @Test
    void shouldInheritTenantIdAndIgnoreFlagToChildThread() throws InterruptedException {
        TenantContextHolder.setTenantId(600L);
        TenantContextHolder.setIgnore(true);
        AtomicReference<Long> otherThreadTenantId = new AtomicReference<>();
        AtomicReference<Boolean> otherThreadIgnore = new AtomicReference<>();

        Thread otherThread = new Thread(() -> {
            otherThreadTenantId.set(TenantContextHolder.getTenantId());
            otherThreadIgnore.set(TenantContextHolder.isIgnore());
        });
        otherThread.start();
        otherThread.join();

        assertThat(otherThreadTenantId).hasValue(600L);
        assertThat(otherThreadIgnore).hasValue(true);
    }

    @Test
    void shouldNotPropagateChildTenantIdMutationToParent() throws InterruptedException {
        TenantContextHolder.setTenantId(700L);

        Thread otherThread = new Thread(() -> TenantContextHolder.setTenantId(999L));
        otherThread.start();
        otherThread.join();

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(700L);
    }

    @Test
    void shouldNotPropagateChildIgnoreFlagMutationToParent() throws InterruptedException {
        TenantContextHolder.setIgnore(true);

        Thread otherThread = new Thread(() -> TenantContextHolder.setIgnore(false));
        otherThread.start();
        otherThread.join();

        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    @Test
    void shouldNotInheritTenantIdAfterParentClear() throws InterruptedException {
        TenantContextHolder.setTenantId(800L);
        TenantContextHolder.clear();
        AtomicReference<Long> otherThreadTenantId = new AtomicReference<>();

        Thread otherThread = new Thread(() -> otherThreadTenantId.set(TenantContextHolder.getTenantId()));
        otherThread.start();
        otherThread.join();

        assertThat(otherThreadTenantId.get()).isNull();
    }

    @Test
    void shouldNotInheritIgnoreFlagAfterParentClear() throws InterruptedException {
        TenantContextHolder.setIgnore(true);
        TenantContextHolder.clear();
        AtomicReference<Boolean> otherThreadIgnore = new AtomicReference<>();

        Thread otherThread = new Thread(() -> otherThreadIgnore.set(TenantContextHolder.isIgnore()));
        otherThread.start();
        otherThread.join();

        assertThat(otherThreadIgnore).hasValue(false);
    }

    @Test
    void shouldPropagateTenantContextThroughDecoratedExecutor() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            TenantContextHolder.setTenantId(900L);
            TenantContextHolder.setIgnore(true);

            TenantSnapshot snapshot = submitWithTenantContext(executor);

            assertThat(snapshot.tenantId()).isEqualTo(900L);
            assertThat(snapshot.ignore()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotLeakTenantContextBetweenDecoratedExecutorTasks() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            TenantContextHolder.setTenantId(1000L);
            TenantContextHolder.setIgnore(true);
            TenantSnapshot first = submitWithTenantContext(executor);

            TenantContextHolder.clear();
            TenantSnapshot second = submitWithTenantContext(executor);

            TenantContextHolder.setTenantId(1001L);
            TenantContextHolder.setIgnore(false);
            TenantSnapshot third = submitWithTenantContext(executor);

            assertThat(first.tenantId()).isEqualTo(1000L);
            assertThat(first.ignore()).isTrue();
            assertThat(second.tenantId()).isNull();
            assertThat(second.ignore()).isFalse();
            assertThat(third.tenantId()).isEqualTo(1001L);
            assertThat(third.ignore()).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    private TenantSnapshot submitWithTenantContext(ExecutorService executor) throws Exception {
        AtomicReference<TenantSnapshot> snapshot = new AtomicReference<>();
        TenantTaskDecorator decorator = new TenantTaskDecorator();
        Future<?> future = executor.submit(decorator.decorate(() -> snapshot.set(new TenantSnapshot(
                TenantContextHolder.getTenantId(),
                TenantContextHolder.isIgnore()))));
        future.get(3, TimeUnit.SECONDS);
        return snapshot.get();
    }

    private record TenantSnapshot(Long tenantId, boolean ignore) {
    }
}
