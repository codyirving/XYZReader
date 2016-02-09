package com.example.xyzreader.ui;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.util.List;
import java.util.Map;


/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {


    static final String EXTRA_STARTING_ALBUM_POSITION = "extra_starting_item_position";
    static final String EXTRA_CURRENT_ALBUM_POSITION = "extra_current_item_position";
    int startingPosition;
    int currentPosition;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private Bundle mTmpReenterState;
    private Adapter adapter;
    private final SharedElementCallback mCallback = new SharedElementCallback() {
        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (mTmpReenterState != null) {
                    int startingPosition = mTmpReenterState.getInt(EXTRA_STARTING_ALBUM_POSITION);
                    int currentPosition = mTmpReenterState.getInt(EXTRA_CURRENT_ALBUM_POSITION);

                    if (startingPosition != currentPosition) {
                        //If startingPosition != currentPosition then the user must have swiped
                        //and we need to update the shared element for proper animationtransitions
                        adapter.mCursor.moveToPosition(currentPosition);
                        String newTransitionName = adapter.mCursor.getString(ArticleLoader.Query.TITLE);
                        View newSharedElement = mRecyclerView.findViewWithTag(newTransitionName);

                        if (newSharedElement != null) {

                            names.clear();
                            names.add(newTransitionName);
                            sharedElements.clear();
                            sharedElements.put(newTransitionName, newSharedElement);
                        }
                    }
                    mTmpReenterState = null;
                } else {

                    //if mTmpReenterState is null then activity is exiting. Taken straight from example
                    View navigationBar = findViewById(android.R.id.navigationBarBackground);
                    View statusBar = findViewById(android.R.id.statusBarBackground);

                    if (navigationBar != null) {
                        names.add(navigationBar.getTransitionName());
                        sharedElements.put(navigationBar.getTransitionName(), navigationBar);
                    }
                    if (statusBar != null) {
                        names.add(statusBar.getTransitionName());
                        sharedElements.put(statusBar.getTransitionName(), statusBar);
                    }
                }
            }
        }
    };
    private boolean mIsDetailsActivityStarted;
    private boolean loaderRestarting = false;
    private boolean mIsRefreshing = false;
    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }

    };

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
        setContentView(R.layout.activity_article_list);
        setExitSharedElementCallback(mCallback);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mIsRefreshing = false;
                mSwipeRefreshLayout.setRefreshing(false);
            }


        });
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        if (savedInstanceState == null) {
            refresh();
        }
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsDetailsActivityStarted = false;
    }

    @Override
    public void onActivityReenter(int requestCode, Intent data) {

        super.onActivityReenter(requestCode, data);
        mTmpReenterState = new Bundle(data.getExtras());
        startingPosition = mTmpReenterState.getInt(EXTRA_STARTING_ALBUM_POSITION);
        currentPosition = mTmpReenterState.getInt(EXTRA_CURRENT_ALBUM_POSITION);

        LoaderManager lm = getLoaderManager();
        if (adapter != null) {
            lm.initLoader(0, null, this);

        } else {
            loaderRestarting = true;
            lm.restartLoader(0, null, this);

        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            postponeEnterTransition();
        }
        //getLoaderManager().initLoader(0, null, this); //too slow

        if (!loaderRestarting && startingPosition != currentPosition) {
            mRecyclerView.scrollToPosition(currentPosition);

        }

        mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                // TODO: figure out why it is necessary to request layout here in order to get a smooth transition.
                mRecyclerView.requestLayout();
                if (!loaderRestarting) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        startPostponedEnterTransition();
                    }
                }

                return true;
            }
        });
    }

    private void updateRefreshingUI() {


        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {

        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);

        mRecyclerView.setLayoutManager(sglm);
        mRecyclerView.requestLayout();

        if (loaderRestarting) {
            if (startingPosition != currentPosition) {
                mRecyclerView.scrollToPosition(currentPosition);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    loaderRestarting = false;

                }
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            holder.subtitleView.setText(
                    DateUtils.getRelativeTimeSpanString(
                            mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString()
                            + " by "
                            + mCursor.getString(ArticleLoader.Query.AUTHOR));
            holder.thumbnailView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.THUMB_URL),
                    ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));
            holder.bind(position, mCursor);
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            super.onAttachedToRecyclerView(recyclerView);
        }

        @Override
        public void onViewAttachedToWindow(ViewHolder holder) {
            super.onViewAttachedToWindow(holder);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startPostponedEnterTransition();
            }
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final ImageView mAlbumImage;
        public DynamicHeightNetworkImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;
        private Cursor mCursor;
        private int mAlbumPosition;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
            mAlbumImage = (ImageView) view.findViewById(R.id.thumbnail);
            view.setOnClickListener(this);
        }

        public void bind(int position, Cursor mCursor) {
            this.mCursor = mCursor;
            String title = mCursor.getString(ArticleLoader.Query.TITLE);
            mAlbumPosition = position;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mAlbumImage.setTransitionName(title);
                mAlbumImage.setTag(title);
            }
        }

        @Override

        public void onClick(View view) {
            mCursor.moveToPosition(mAlbumPosition);
            long itemID = mCursor.getLong(ArticleLoader.Query._ID);
            Intent newActivity = new Intent(Intent.ACTION_VIEW,
                    ItemsContract.Items.buildItemUri(itemID));
            newActivity.putExtra(EXTRA_STARTING_ALBUM_POSITION, mAlbumPosition);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (!mIsDetailsActivityStarted) {
                    mIsDetailsActivityStarted = true;

                    startActivity(newActivity, ActivityOptionsCompat.makeSceneTransitionAnimation(ArticleListActivity.this,
                            mAlbumImage, mAlbumImage.getTransitionName()).toBundle());
                }
            } else {

                startActivity(newActivity);
            }
        }
    }
}

