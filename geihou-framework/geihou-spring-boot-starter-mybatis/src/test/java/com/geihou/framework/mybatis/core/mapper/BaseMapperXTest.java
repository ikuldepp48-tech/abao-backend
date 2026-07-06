package com.geihou.framework.mybatis.core.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.geihou.common.pojo.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BaseMapperXTest {

    @Test
    void shouldExtendMybatisPlusBaseMapper() {
        assertTrue(BaseMapper.class.isAssignableFrom(BaseMapperX.class));
    }

    @Test
    void selectOneShouldDelegateWithSingleFieldWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        TestDataObject expected = new TestDataObject();
        doReturn(expected).when(mapper).selectOne(anyWrapper());

        TestDataObject result = mapper.selectOne(TestDataObject::getName, "alpha");

        assertSame(expected, result);
        assertLambdaWrapper(captureSelectOneWrapper(mapper));
    }

    @Test
    void selectOneShouldDelegateWithTwoFieldWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        TestDataObject expected = new TestDataObject();
        doReturn(expected).when(mapper).selectOne(anyWrapper());

        TestDataObject result = mapper.selectOne(TestDataObject::getName, "alpha", TestDataObject::getStatus, "ACTIVE");

        assertSame(expected, result);
        assertLambdaWrapper(captureSelectOneWrapper(mapper));
    }

    @Test
    void selectListShouldDelegateWithEmptyWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        List<TestDataObject> expected = List.of(new TestDataObject());
        doReturn(expected).when(mapper).selectList(anyWrapper());

        List<TestDataObject> result = mapper.selectList();

        assertSame(expected, result);
        assertLambdaWrapper(captureSelectListWrapper(mapper));
    }

    @Test
    void selectListShouldDelegateWithSingleFieldWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        List<TestDataObject> expected = List.of(new TestDataObject());
        doReturn(expected).when(mapper).selectList(anyWrapper());

        List<TestDataObject> result = mapper.selectList(TestDataObject::getName, "alpha");

        assertSame(expected, result);
        assertLambdaWrapper(captureSelectListWrapper(mapper));
    }

    @Test
    void selectListShouldDelegateWithTwoFieldWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        List<TestDataObject> expected = List.of(new TestDataObject());
        doReturn(expected).when(mapper).selectList(anyWrapper());

        List<TestDataObject> result = mapper.selectList(
                TestDataObject::getName, "alpha", TestDataObject::getStatus, "ACTIVE");

        assertSame(expected, result);
        assertLambdaWrapper(captureSelectListWrapper(mapper));
    }

    @Test
    void selectCountShouldDelegateWithSingleFieldWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        doReturn(3L).when(mapper).selectCount(anyWrapper());

        Long result = mapper.selectCount(TestDataObject::getStatus, "ACTIVE");

        assertEquals(3L, result);
        assertLambdaWrapper(captureSelectCountWrapper(mapper));
    }

    @Test
    void deleteShouldDelegateWithSingleFieldWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        doReturn(2).when(mapper).delete(anyWrapper());

        int result = mapper.delete(TestDataObject::getStatus, "INACTIVE");

        assertEquals(2, result);
        assertLambdaWrapper(captureDeleteWrapper(mapper));
    }

    @Test
    void selectPageShouldDelegateWithEmptyWrapperAndConvertResult() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        stubSelectPage(mapper, pageResult(2, 10, 30, List.of(new TestDataObject(), new TestDataObject())));

        PageResult<TestDataObject> result = mapper.selectPage(2, 10);

        assertEquals(2, result.getList().size());
        assertEquals(30L, result.getTotal());
        assertEquals(2, result.getPageNo());
        assertEquals(10, result.getPageSize());
        CapturedPageCall captured = captureSelectPageCall(mapper);
        assertEquals(2L, captured.page().getCurrent());
        assertEquals(10L, captured.page().getSize());
        assertLambdaWrapper(captured.wrapper());
    }

    @Test
    void selectPageShouldUseExplicitWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        stubSelectPage(mapper, pageResult(1, 20, 1, List.of(new TestDataObject())));
        Wrapper<TestDataObject> wrapper = new LambdaQueryWrapper<TestDataObject>().eq(TestDataObject::getStatus, "ACTIVE");

        PageResult<TestDataObject> result = mapper.selectPage(1, 20, wrapper);

        assertEquals(1L, result.getTotal());
        CapturedPageCall captured = captureSelectPageCall(mapper);
        assertEquals(1L, captured.page().getCurrent());
        assertEquals(20L, captured.page().getSize());
        assertSame(wrapper, captured.wrapper());
    }

    @Test
    void selectPageShouldDelegateWithSingleFieldWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        stubSelectPage(mapper, pageResult(3, 15, 2, List.of(new TestDataObject())));

        PageResult<TestDataObject> result = mapper.selectPage(TestDataObject::getStatus, "ACTIVE", 3, 15);

        assertEquals(2L, result.getTotal());
        CapturedPageCall captured = captureSelectPageCall(mapper);
        assertEquals(3L, captured.page().getCurrent());
        assertEquals(15L, captured.page().getSize());
        assertLambdaWrapper(captured.wrapper());
    }

    @Test
    void selectPageShouldDelegateWithTwoFieldWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();
        stubSelectPage(mapper, pageResult(4, 25, 0, List.of()));

        PageResult<TestDataObject> result = mapper.selectPage(
                TestDataObject::getName, "alpha", TestDataObject::getStatus, "ACTIVE", 4, 25);

        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
        CapturedPageCall captured = captureSelectPageCall(mapper);
        assertEquals(4L, captured.page().getCurrent());
        assertEquals(25L, captured.page().getSize());
        assertLambdaWrapper(captured.wrapper());
    }

    @Test
    void selectPageShouldRejectNullPageNo() {
        BaseMapperX<TestDataObject> mapper = mockMapper();

        assertThrows(NullPointerException.class, () -> mapper.selectPage(null, 10));
    }

    @Test
    void selectPageShouldRejectPageNoLessThanOne() {
        BaseMapperX<TestDataObject> mapper = mockMapper();

        assertThrows(IllegalArgumentException.class, () -> mapper.selectPage(0, 10));
    }

    @Test
    void selectPageShouldRejectNullPageSize() {
        BaseMapperX<TestDataObject> mapper = mockMapper();

        assertThrows(NullPointerException.class, () -> mapper.selectPage(1, null));
    }

    @Test
    void selectPageShouldRejectPageSizeLessThanOne() {
        BaseMapperX<TestDataObject> mapper = mockMapper();

        assertThrows(IllegalArgumentException.class, () -> mapper.selectPage(1, 0));
    }

    @Test
    void selectPageShouldRejectNullWrapper() {
        BaseMapperX<TestDataObject> mapper = mockMapper();

        assertThrows(NullPointerException.class, () -> mapper.selectPage(1, 10, null));
    }

    @SuppressWarnings("unchecked")
    private static BaseMapperX<TestDataObject> mockMapper() {
        return mock(BaseMapperX.class, CALLS_REAL_METHODS);
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<TestDataObject> anyWrapper() {
        return any(Wrapper.class);
    }

    private static Wrapper<TestDataObject> captureSelectOneWrapper(BaseMapperX<TestDataObject> mapper) {
        ArgumentCaptor<Wrapper<TestDataObject>> captor = wrapperCaptor();
        verify(mapper).selectOne(captor.capture());
        return captor.getValue();
    }

    private static Wrapper<TestDataObject> captureSelectListWrapper(BaseMapperX<TestDataObject> mapper) {
        ArgumentCaptor<Wrapper<TestDataObject>> captor = wrapperCaptor();
        verify(mapper).selectList(captor.capture());
        return captor.getValue();
    }

    private static Wrapper<TestDataObject> captureSelectCountWrapper(BaseMapperX<TestDataObject> mapper) {
        ArgumentCaptor<Wrapper<TestDataObject>> captor = wrapperCaptor();
        verify(mapper).selectCount(captor.capture());
        return captor.getValue();
    }

    private static Wrapper<TestDataObject> captureDeleteWrapper(BaseMapperX<TestDataObject> mapper) {
        ArgumentCaptor<Wrapper<TestDataObject>> captor = wrapperCaptor();
        verify(mapper).delete(captor.capture());
        return captor.getValue();
    }

    private static void stubSelectPage(BaseMapperX<TestDataObject> mapper, IPage<TestDataObject> resultPage) {
        doReturn(resultPage).when(mapper).selectPage(anyPage(), anyWrapper());
    }

    private static CapturedPageCall captureSelectPageCall(BaseMapperX<TestDataObject> mapper) {
        ArgumentCaptor<IPage<TestDataObject>> pageCaptor = pageCaptor();
        ArgumentCaptor<Wrapper<TestDataObject>> wrapperCaptor = wrapperCaptor();
        verify(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        return new CapturedPageCall(pageCaptor.getValue(), wrapperCaptor.getValue());
    }

    private static IPage<TestDataObject> pageResult(long current, long size, long total, List<TestDataObject> records) {
        Page<TestDataObject> page = new Page<>(current, size);
        page.setTotal(total);
        page.setRecords(records);
        return page;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Wrapper<TestDataObject>> wrapperCaptor() {
        return ArgumentCaptor.forClass((Class) Wrapper.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<IPage<TestDataObject>> pageCaptor() {
        return ArgumentCaptor.forClass((Class) IPage.class);
    }

    @SuppressWarnings("unchecked")
    private static IPage<TestDataObject> anyPage() {
        return any(IPage.class);
    }

    private static void assertLambdaWrapper(Wrapper<TestDataObject> wrapper) {
        assertTrue(wrapper instanceof LambdaQueryWrapper<?>);
    }

    private record CapturedPageCall(IPage<TestDataObject> page, Wrapper<TestDataObject> wrapper) {
    }

    static class TestDataObject {

        private String name;
        private String status;

        public String getName() {
            return name;
        }

        public String getStatus() {
            return status;
        }
    }
}
