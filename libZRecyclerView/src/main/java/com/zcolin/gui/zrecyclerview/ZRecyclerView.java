/*
 * *********************************************************
 *   author   colin
 *   company  fosung
 *   email    wanglin2046@126.com
 *   date     16-12-19 上午11:28
 * ********************************************************
 */

package com.zcolin.gui.zrecyclerview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.zcolin.gui.zrecyclerview.loadmorefooter.DefLoadMoreFooter;
import com.zcolin.gui.zrecyclerview.loadmorefooter.ILoadMoreFooter;
import com.zcolin.gui.zrecyclerview.swiperefreshlayout.SwipeRefreshLayout;
import com.zcolin.gui.zrecyclerview.swiperefreshlayout.ZSwipeRefreshLayout;

import java.util.List;


/**
 * <p/>
 * RecyclerView下拉刷新和上拉加载更多以及RecyclerView线性、网格、瀑布流
 * <p/>
 */
public class ZRecyclerView extends FrameLayout {
    private PullLoadMoreListener mPullLoadMoreListener;
    private View                 headerView;
    private View                 footerView;
    private boolean isRefreshEnabled  = true; //设置下拉刷新是否可用
    private boolean isLoadMoreEnabled = true; //设置到底加载是否可用
    private boolean isShowNoMore      = true; //是否显示 加载全部

    private boolean isNoMore      = false;
    private boolean isRefreshing  = false;
    private boolean isLoadingData = false;
    private BaseRecyclerAdapter.OnItemClickListener     itemClickListener;
    private BaseRecyclerAdapter.OnItemLongClickListener itemLongClickListener;
    private long minClickIntervaltime = 100; //ITEM点击的最小间隔

    private WrapperRecyclerAdapter    mWrapAdapter;
    private ILoadMoreFooter           loadMoreFooter;
    private RecyclerView              mRecyclerView;
    private ZSwipeRefreshLayout       mSwipeRefreshLayout;
    private RelativeLayout            mEmptyViewContainer;
    private Context                   mContext;
    private ChangeScrollStateCallback mChangeScrollStateCallback;
    private RecyclerView.AdapterDataObserver mEmptyDataObserver = new DataObserver();
    private boolean hasRegisterEmptyObserver;
    private Handler handler = new Handler(Looper.getMainLooper());

    public ZRecyclerView(Context context) {
        this(context, null);
    }

    public ZRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZRecyclerView(Context context, AttributeSet attrs, int resId) {
        super(context, attrs);
        initView(context, resId);
    }

    private void initView(Context context, int resId) {
        mContext = context;
        View view = LayoutInflater.from(context)
                                  .inflate(resId > 0 ? resId : R.layout.gui_zrecyclerview_zrecycler, null);
        mSwipeRefreshLayout = (ZSwipeRefreshLayout) view.findViewById(R.id.swipeRefreshLayout);
        mSwipeRefreshLayout.setColorSchemeResources(android.R.color.holo_green_dark, android.R.color.holo_blue_dark, android.R.color.holo_orange_dark);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayoutOnRefresh(this));

        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        mRecyclerView.setVerticalScrollBarEnabled(true);

        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.addOnScrollListener(new RecyclerViewOnScroll(this));
        mRecyclerView.setOnTouchListener(new onTouchRecyclerView());
        setLinearLayout(false);
        mEmptyViewContainer = (RelativeLayout) view.findViewById(R.id.emptyView);
        mEmptyViewContainer.setVisibility(View.GONE);

        if (isLoadMoreEnabled) {
            setLoadMoreFooter(new DefLoadMoreFooter(getContext()));
        }

        this.addView(view);
    }

    /**
     * 设置下拉刷新上拉加载回调
     */
    public ZRecyclerView setOnPullLoadMoreListener(PullLoadMoreListener listener) {
        mPullLoadMoreListener = listener;
        return this;
    }

    /**
     * 此处设置OnItemClickListener是调用的{@link BaseRecyclerAdapter#setOnItemClickListener(BaseRecyclerAdapter.OnItemClickListener)}，
     * 此处的泛型类型必须和{@link BaseRecyclerAdapter}的相同
     */
    public <T> ZRecyclerView setOnItemClickListener(BaseRecyclerAdapter.OnItemClickListener<T> li) {
        itemClickListener = li;
        if (mWrapAdapter != null) {
            if (mWrapAdapter.getAdapter() instanceof BaseRecyclerAdapter) {
                ((BaseRecyclerAdapter) mWrapAdapter.getAdapter()).setOnItemClickListener(li);
            } else {
                throw new IllegalArgumentException("adapter 必须继承BaseRecyclerAdapter 才能使用setOnItemClickListener");
            }
        }
        return this;
    }

    /**
     * 此处设置OnItemLongClickListener是调用的{@link BaseRecyclerAdapter#setOnItemLongClickListener(BaseRecyclerAdapter.OnItemLongClickListener)}，
     * 此处的泛型类型必须和{@link BaseRecyclerAdapter}的相同
     */
    public <T> ZRecyclerView setOnItemLongClickListener(BaseRecyclerAdapter.OnItemLongClickListener<T> li) {
        itemLongClickListener = li;
        if (mWrapAdapter != null) {
            if (mWrapAdapter.getAdapter() instanceof BaseRecyclerAdapter) {
                ((BaseRecyclerAdapter) mWrapAdapter.getAdapter()).setOnItemLongClickListener(li);
            } else {
                throw new IllegalArgumentException("adapter 必须继承BaseRecyclerAdapter 才能使用setOnItemLongClickListener");
            }
        }
        return this;
    }

    /**
     * 设置Item点击的最小间隔
     *
     * @param minClickIntervaltime millionSeconds
     */
    public ZRecyclerView setItemMinClickIntervalTime(long minClickIntervaltime) {
        this.minClickIntervaltime = minClickIntervaltime;
        if (mWrapAdapter != null) {
            if (mWrapAdapter.getAdapter() instanceof BaseRecyclerAdapter) {
                ((BaseRecyclerAdapter) mWrapAdapter.getAdapter()).setItemMinClickIntervalTime(minClickIntervaltime);
            } else {
                throw new IllegalArgumentException("adapter 必须继承BaseRecyclerAdapter 才能使用setItemMinClickIntervalTime");
            }
        }
        return this;
    }

    /**
     * LinearLayoutManager
     *
     * @param isForce 如果已经设置了，是否强制设置
     */
    public ZRecyclerView setLinearLayout(boolean isForce) {
        if (isForce || getLayoutManager() == null || !(getLayoutManager() instanceof LinearLayoutManager)) {
            LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
            linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            mRecyclerView.setLayoutManager(linearLayoutManager);
        }
        return this;
    }

    /**
     * GridLayoutManager
     *
     * @param isForce 如果已经设置了，是否强制设置
     */
    public ZRecyclerView setGridLayout(boolean isForce, int spanCount) {
        if (isForce || getLayoutManager() == null || !(getLayoutManager() instanceof GridLayoutManager)) {
            GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), spanCount);
            gridLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
            mRecyclerView.setLayoutManager(gridLayoutManager);
        }
        return this;
    }

    /**
     * StaggeredGridLayoutManager
     *
     * @param isForce 如果已经设置了，是否强制设置
     */
    public ZRecyclerView setStaggeredGridLayout(boolean isForce, int spanCount) {
        if (isForce || getLayoutManager() == null || !(getLayoutManager() instanceof StaggeredGridLayoutManager)) {
            StaggeredGridLayoutManager staggeredGridLayoutManager = new StaggeredGridLayoutManager(spanCount, LinearLayoutManager.VERTICAL);
            mRecyclerView.setLayoutManager(staggeredGridLayoutManager);
        }
        return this;
    }

    /**
     * 增加默认分割线
     */
    public ZRecyclerView addDefaultItemDecoration() {
        mRecyclerView.addItemDecoration(new ZRecycleViewDivider(mContext, LinearLayout.HORIZONTAL));
        return this;
    }

    /**
     * 增加分割线
     *
     * @param itemDecoration ex:zRecyclerView.addItemDecoration(new RecycleViewDivider(this, LinearLayout.VERTICAL));
     */
    public ZRecyclerView addItemDecoration(RecyclerView.ItemDecoration itemDecoration) {
        mRecyclerView.addItemDecoration(itemDecoration);
        return this;
    }

    /**
     * 增加分割线
     *
     * @param itemDecoration ex:zRecyclerView.addItemDecoration(new RecycleViewDivider(this, LinearLayout.VERTICAL), 2);
     */
    public ZRecyclerView addItemDecoration(RecyclerView.ItemDecoration itemDecoration, int index) {
        mRecyclerView.addItemDecoration(itemDecoration, index);
        return this;
    }


    /**
     * 获取RecyclerVIew的LayoutManager
     */
    public RecyclerView.LayoutManager getLayoutManager() {
        return mRecyclerView.getLayoutManager();
    }

    /**
     * 此类为FrameLayout，获取包含的子view - RecyclerView，
     */
    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    /**
     * 获取加载更多的Footer
     */
    public View getLoadMoreFooterView() {
        return loadMoreFooter.getFootView();
    }

    /**
     * 获取设置的HeaderView
     */
    public View getHeaderView() {
        return headerView;
    }

    /**
     * 获取设置的FooterView
     */
    public View getFooterView() {
        return footerView;
    }

    /**
     * 设置自定义的HeaderView
     */
    public ZRecyclerView setHeaderView(View headerView) {
        this.headerView = headerView;
        if (headerView != null) {
            this.headerView.setTag("reservedView");
        }

        if (mWrapAdapter != null) {
            mWrapAdapter.setHeaderView(headerView);
        }

        return this;
    }

    /**
     * 设置自定义的HeaderView
     */
    public ZRecyclerView setHeaderView(Context context, int headerViewLayoutId) {
        return setHeaderView(LayoutInflater.from(context)
                                           .inflate(headerViewLayoutId, null));
    }

    /**
     * 设置自定义的FooterView
     */
    public ZRecyclerView setFooterView(View footerView) {
        this.footerView = footerView;
        if (footerView != null) {
            this.footerView.setTag("reservedView");
        }
        return this;

    }

    /**
     * 设置自定义的FooterView
     */
    public ZRecyclerView setFooterView(Context context, int footerViewLayoutId) {
        return setFooterView(LayoutInflater.from(context)
                                           .inflate(footerViewLayoutId, null));
    }

    /**
     * 设置自定义的加载更多FooterView
     */
    public ZRecyclerView setLoadMoreFooter(ILoadMoreFooter loadMoreFooter) {
        this.loadMoreFooter = loadMoreFooter;
        this.loadMoreFooter.getFootView()
                           .setTag("reservedView");
        return this;
    }

    /**
     * 设置到底加载是否可用
     */
    public ZRecyclerView setIsLoadMoreEnabled(boolean enabled) {
        isLoadMoreEnabled = enabled;
        if (!enabled && loadMoreFooter != null) {
            loadMoreFooter.onReset();
        }
        return this;
    }

    /**
     * 到底加载是否可用
     */
    public boolean isLoadMoreEnabled() {
        return isLoadMoreEnabled;
    }

    /**
     * 设置下拉刷新是否可用
     */
    public ZRecyclerView setIsRefreshEnabled(boolean enabled) {
        mSwipeRefreshLayout.setEnabled(enabled);
        isRefreshEnabled = enabled;
        return this;
    }

    /**
     * 下拉刷新是否可用
     */
    public boolean isRefreshEnabled() {
        return isRefreshEnabled;
    }

    /**
     * 设置是否显示NoMoreView
     */
    public ZRecyclerView setIsShowNoMore(boolean isShowNoMore) {
        this.isShowNoMore = isShowNoMore;
        return this;
    }

    /**
     * 是否显示NoMoreView
     */
    public boolean isShowNoMore() {
        return isShowNoMore;
    }

    /**
     * 设置加载进度条View,此处可以设置 https://github.com/81813780/AVLoadingIndicatorView 的view
     */
    public ZRecyclerView setLoadMoreProgressView(View view) {
        if (loadMoreFooter != null && loadMoreFooter instanceof DefLoadMoreFooter) {
            ((DefLoadMoreFooter) loadMoreFooter).setProgressView(view);
        }
        return this;
    }

    /**
     * 设置加载进度条文字（如果LoadMoreFooter为DefLoadMoreFooter）
     *
     * @param text1 加载中
     * @param text2 加载完成
     * @param text3 已加载全部
     */
    public ZRecyclerView setLoadMoreText(String text1, String text2, String text3) {
        if (loadMoreFooter != null && loadMoreFooter instanceof DefLoadMoreFooter) {
            ((DefLoadMoreFooter) loadMoreFooter).setText(text1, text2, text3);
        }
        return this;
    }

    /**
     * 设置加载进度条文字颜色（如果LoadMoreFooter为DefLoadMoreFooter）
     */
    public ZRecyclerView setLoadMoreTextColor(int textColor) {
        if (loadMoreFooter != null && loadMoreFooter instanceof DefLoadMoreFooter) {
            ((DefLoadMoreFooter) loadMoreFooter).setTextColor(textColor);
        }
        return this;
    }

    /**
     * 设置加载进度条文字大小（如果LoadMoreFooter为DefLoadMoreFooter）
     */
    public ZRecyclerView setLoadMoreTextSize(float textSize) {
        if (loadMoreFooter != null && loadMoreFooter instanceof DefLoadMoreFooter) {
            ((DefLoadMoreFooter) loadMoreFooter).setTextSize(textSize);
        }
        return this;
    }

    /**
     * 设置没有数据时显示的EmptyView
     */
    public ZRecyclerView setEmptyView(View emptyView) {
        mEmptyViewContainer.removeAllViews();
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        mEmptyViewContainer.addView(emptyView, params);
        return this;
    }

    /**
     * 设置没有数据时显示的EmptyView
     */
    public ZRecyclerView setEmptyView(Context context, int layoutId) {
        return setEmptyView(LayoutInflater.from(context)
                                          .inflate(layoutId, null));
    }

    /**
     * 返回的放置emptyView的RelativeLayout
     */
    public View getEmptyViewContainer() {
        return mEmptyViewContainer;
    }

    /**
     * 返回的放置emptyView的RelativeLayout
     */
    public View getEmptyView() {
        return mEmptyViewContainer.getChildCount() > 0 ? mEmptyViewContainer.getChildAt(0) : null;
    }

    /**
     * 使RecyclerView滚动到顶部
     */
    public void scrollToTop() {
        mRecyclerView.scrollToPosition(0);
    }

    /**
     * 使RecyclerView滚动到顶部
     */
    public void scrollToBottom() {
        mRecyclerView.scrollToPosition(getHeight());
    }


    public boolean isSwipeRefreshEnable() {
        return mSwipeRefreshLayout.isEnabled();
    }

    public void setSwipeRefreshEnable(boolean enable) {
        mSwipeRefreshLayout.setEnabled(enable);
    }

    /**
     * 设置SwipeRefreshLayout的colorScheme
     */
    public ZRecyclerView setColorSchemeResources(int... colorResIds) {
        mSwipeRefreshLayout.setColorSchemeResources(colorResIds);
        return this;
    }

    /**
     * 此类为FrameLayout，获取包含的子view - SwipeRefreshLayout，
     */
    public SwipeRefreshLayout getSwipeRefreshLayout() {
        return mSwipeRefreshLayout;
    }

    /**
     * 设置是否处理冲突（如Viewpager），默认不处理
     * 如果嵌套有viewpager等控件，需要设置isProceeConflict为true
     */
    public void setIsProceeConflict(boolean isProceeConflict) {
        mSwipeRefreshLayout.setIsProceeConflict(isProceeConflict);
    }

    /**
     * 手动调用直接刷新，无下拉效果
     */
    public void refresh() {
        if (mPullLoadMoreListener != null) {
            isRefreshing = true;
            mPullLoadMoreListener.onRefresh();
        }
    }

    /**
     * 手动调用下拉刷新，有下拉效果
     */
    public void refreshWithPull() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isRefreshEnabled) {
                    mSwipeRefreshLayout.setRefreshing(true);
                    refresh();
                }
            }
        }, 300);
    }

    /**
     * 手动调用加载状态，此函数不会调用 {@link PullLoadMoreListener#onRefresh()}加载数据
     * 如果需要加载数据和状态显示调用 {@link #refreshWithPull()}
     */
    public void setRefreshing(final boolean isRefresh) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isRefreshEnabled) {
                    isRefreshing = isRefresh;
                    mSwipeRefreshLayout.setRefreshing(isRefresh);
                }
            }
        });
    }

    /**
     * 手动调用直接加载更多
     */
    public void loadMore() {
        if (mPullLoadMoreListener != null && !isNoMore) {
            this.isLoadingData = true;
            mPullLoadMoreListener.onLoadMore();
        }
    }

    /**
     * 手动调用直接加载更多
     */
    public void loadMoreWithLoading() {
        if (mPullLoadMoreListener != null && !isNoMore) {
            this.isLoadingData = true;
            loadMoreFooter.onLoading();
            mPullLoadMoreListener.onLoadMore();
        }
    }

    /**
     * 下拉刷新和到底加载完成
     */
    public void setPullLoadMoreCompleted() {
        if (isRefreshing) {
            isRefreshing = false;
            setRefreshing(false);
        }

        isLoadingData = false;
        //已经设置了nomore了，就不在用完成状态覆盖nomore状态
        if (!isNoMore) {
            loadMoreFooter.onComplete();
        }
    }

    /**
     * 设置是否已加载全部<br>
     * 设置之后到底{@link PullLoadMoreListener#onLoadMore()}不会再调用除非再次调用{@link #setNoMore(boolean)}为true;
     * 一般做法是在{@link PullLoadMoreListener#onRefresh()}中设置{@link #setNoMore(boolean)}为true;
     */
    public void setNoMore(boolean noMore) {
        setNoMore(noMore, 0, 0);
    }

    /**
     * 设置是否已加载全部, <br>
     * 设置之后到底{@link PullLoadMoreListener#onLoadMore()}不会再调用除非再次调用{@link #setNoMore(boolean)}为true;
     * 一般做法是在{@link PullLoadMoreListener#onRefresh()}中设置{@link #setNoMore(boolean)}为true;
     */
    public void setNoMore(boolean noMore, int minShowItem, List<?> data) {
        setNoMore(noMore, minShowItem, data == null ? 0 : data.size());
    }

    /**
     * 设置是否已加载全部, <br>
     * 设置之后到底{@link PullLoadMoreListener#onLoadMore()}不会再调用除非再次调用{@link #setNoMore(boolean)}为true;
     * 一般做法是在{@link PullLoadMoreListener#onRefresh()}中设置{@link #setNoMore(boolean)}为true;
     */
    public void setNoMore(boolean noMore, int minShowItem, int dataSize) {
        isLoadingData = false;
        isNoMore = noMore;

        if (isNoMore && dataSize >= minShowItem) {
            loadMoreFooter.onNoMore();
        } else {
            loadMoreFooter.onComplete();
        }
    }

    public boolean isNoMore() {
        return isNoMore;
    }

    public boolean isLoadingData() {
        return isLoadingData;
    }

    public boolean isRefreshing() {
        return isRefreshing;
    }

    public void setAdapter(RecyclerView.Adapter adapter) {
        if (mWrapAdapter != null && mWrapAdapter.getAdapter() != null && hasRegisterEmptyObserver) {
            mWrapAdapter.getAdapter()
                        .unregisterAdapterDataObserver(mEmptyDataObserver);
            hasRegisterEmptyObserver = false;
        }

        mWrapAdapter = new WrapperRecyclerAdapter(adapter);
        mWrapAdapter.setHeaderView(headerView)
                    .setFooterView(footerView)
                    .setLoadMoreFooter(loadMoreFooter)
                    .setIsShowNoMore(isShowNoMore)
                    .setIsLoadMoreEnabled(isLoadMoreEnabled);
        mRecyclerView.setAdapter(mWrapAdapter);

        setOnItemClickListener(itemClickListener);
        setOnItemLongClickListener(itemLongClickListener);
        setItemMinClickIntervalTime(minClickIntervaltime);
        if (!hasRegisterEmptyObserver) {
            adapter.registerAdapterDataObserver(mEmptyDataObserver);
            hasRegisterEmptyObserver = true;
        }
    }


    /**
     * Solve IndexOutOfBoundsException exception
     */
    public class onTouchRecyclerView implements OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return isRefreshing || isLoadingData;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        
        /*设置emptyView的监听者*/
        if (mWrapAdapter != null && mWrapAdapter.getAdapter() != null && !hasRegisterEmptyObserver && mEmptyDataObserver != null) {
            mWrapAdapter.getAdapter()
                        .registerAdapterDataObserver(mEmptyDataObserver);
            hasRegisterEmptyObserver = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        
        /*注销监听者*/
        if (mWrapAdapter != null && mWrapAdapter.getAdapter() != null && hasRegisterEmptyObserver) {
            mWrapAdapter.getAdapter()
                        .unregisterAdapterDataObserver(mEmptyDataObserver);
            hasRegisterEmptyObserver = false;
        }
    }

    /**
     * emptyView的监听者类
     */
    private class DataObserver extends RecyclerView.AdapterDataObserver {
        @Override
        public void onChanged() {
            checkEmptyView();
            mWrapAdapter.notifyDataSetChanged();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            mWrapAdapter.notifyItemRangeInserted(headerView == null ? positionStart : positionStart + 1, itemCount);
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            mWrapAdapter.notifyItemRangeRemoved(headerView == null ? positionStart : positionStart + 1, itemCount);
            checkEmptyView();
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            int from = headerView == null ? fromPosition : fromPosition + 1;
            int to = headerView == null ? toPosition : toPosition + 1;
            mWrapAdapter.notifyItemMoved(from, to);
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            mWrapAdapter.notifyItemRangeChanged(headerView == null ? positionStart : positionStart + 1, itemCount);
            checkEmptyView();
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            mWrapAdapter.notifyItemRangeChanged(headerView == null ? positionStart : positionStart + 1, itemCount, payload);
            checkEmptyView();
        }

        private void checkEmptyView() {
            if (mEmptyViewContainer != null && mEmptyViewContainer.getChildCount() > 0) {
                if (mWrapAdapter.getAdapter()
                                .getItemCount() == 0) {
                    mEmptyViewContainer.setVisibility(View.VISIBLE);

                    //使emptyview居中（除headerview之外）
                    if (headerView != null && mEmptyViewContainer.getLayoutParams() instanceof MarginLayoutParams) {
                        ((MarginLayoutParams) mEmptyViewContainer.getLayoutParams()).topMargin = headerView.getHeight();
                    }
                } else {
                    mEmptyViewContainer.setVisibility(View.GONE);
                }
            }
        }
    }

    class SwipeRefreshLayoutOnRefresh implements ZSwipeRefreshLayout.OnRefreshListener {
        private ZRecyclerView zRecyclerView;

        public SwipeRefreshLayoutOnRefresh(ZRecyclerView pullLoadMoreRecyclerView) {
            this.zRecyclerView = pullLoadMoreRecyclerView;
        }

        @Override
        public void onRefresh() {
            if (!zRecyclerView.isRefreshing()) {
                zRecyclerView.refresh();
            }
        }
    }

    public interface PullLoadMoreListener {
        void onRefresh();

        void onLoadMore();
    }

    public class RecyclerViewOnScroll extends RecyclerView.OnScrollListener {
        private ZRecyclerView mPullLoadMoreRecyclerView;

        public RecyclerViewOnScroll(ZRecyclerView pullLoadMoreRecyclerView) {
            this.mPullLoadMoreRecyclerView = pullLoadMoreRecyclerView;
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            int lastCompletelyVisibleItem = 0;
            int firstVisibleItem = 0;
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            int totalItemCount = layoutManager.getItemCount();
            if (layoutManager instanceof GridLayoutManager) {
                GridLayoutManager gridLayoutManager = ((GridLayoutManager) layoutManager);
                //Position to find the final item of the current LayoutManager
                lastCompletelyVisibleItem = gridLayoutManager.findLastCompletelyVisibleItemPosition();
                firstVisibleItem = gridLayoutManager.findFirstCompletelyVisibleItemPosition();
            } else if (layoutManager instanceof LinearLayoutManager) {
                LinearLayoutManager linearLayoutManager = ((LinearLayoutManager) layoutManager);
                lastCompletelyVisibleItem = linearLayoutManager.findLastCompletelyVisibleItemPosition();
                firstVisibleItem = linearLayoutManager.findFirstCompletelyVisibleItemPosition();
            } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                StaggeredGridLayoutManager staggeredGridLayoutManager = ((StaggeredGridLayoutManager) layoutManager);
                // since may lead to the final item has more than one StaggeredGridLayoutManager the particularity of the so here that is an array
                // this array into an array of position and then take the maximum value that is the last show the position value
                int[] lastPositions = new int[((StaggeredGridLayoutManager) layoutManager).getSpanCount()];
                staggeredGridLayoutManager.findLastCompletelyVisibleItemPositions(lastPositions);
                lastCompletelyVisibleItem = findMax(lastPositions);
                firstVisibleItem = staggeredGridLayoutManager.findFirstVisibleItemPositions(lastPositions)[0];
            }

            if (firstVisibleItem == 0 || firstVisibleItem == RecyclerView.NO_POSITION) {
                if (mPullLoadMoreRecyclerView.isRefreshEnabled())
                    mPullLoadMoreRecyclerView.setSwipeRefreshEnable(true);
            } else {
                mPullLoadMoreRecyclerView.setSwipeRefreshEnable(false);
            }
            if (mPullLoadMoreRecyclerView.isLoadMoreEnabled() &&
                    !mPullLoadMoreRecyclerView.isRefreshing()
                    && !mPullLoadMoreRecyclerView.isNoMore()
                    && (lastCompletelyVisibleItem == totalItemCount - 1)
                    && !mPullLoadMoreRecyclerView.isLoadingData()
                    && (dx > 0 || dy > 0)) {
                mPullLoadMoreRecyclerView.loadMoreWithLoading();
            }

        }

        //To find the maximum value in the array
        private int findMax(int[] lastPositions) {
            int max = lastPositions[0];
            for (int value : lastPositions) {
                if (value > max) {
                    max = value;
                }
            }
            return max;
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (mChangeScrollStateCallback != null) {
                mChangeScrollStateCallback.change(newState);
            }
        }

    }

    public interface ChangeScrollStateCallback {
        public void change(int c);
    }

    public void setChangeScrollStateCallback(ChangeScrollStateCallback mChangeScrollStateCallback) {
        this.mChangeScrollStateCallback = mChangeScrollStateCallback;
    }


}
