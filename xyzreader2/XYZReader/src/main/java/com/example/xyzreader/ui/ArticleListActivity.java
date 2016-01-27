package com.example.xyzreader.ui;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.util.Pair;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
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

    private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;

    private Bundle mTmpReenterState;
    static final String EXTRA_STARTING_ALBUM_POSITION = "extra_starting_item_position";
    static final String EXTRA_CURRENT_ALBUM_POSITION = "extra_current_item_position";

    private Adapter adapter;

    private boolean mIsDetailsActivityStarted;

    private final SharedElementCallback mCallback = new SharedElementCallback() {
        @Override
        public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
            //super.onMapSharedElements(names, sharedElements);
            System.out.println("inst.mCallback:ArticleListActivity");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (mTmpReenterState != null) {
                    int startingPosition = mTmpReenterState.getInt(EXTRA_STARTING_ALBUM_POSITION);
                    int currentPosition = mTmpReenterState.getInt(EXTRA_CURRENT_ALBUM_POSITION);
                    System.out.println("Setting pos in ArticleListActivity start: " + startingPosition + "  current: " + currentPosition);
                    if (startingPosition != currentPosition) {
                        //If startingPosition != currentPosition then the user must have swiped
                        //and we need to update the shared element for proper animationtransitions
                        adapter.mCursor.moveToPosition(currentPosition);
                        String newTransitionName = adapter.mCursor.getString(ArticleLoader.Query.TITLE);
                        View newSharedElement = mRecyclerView.findViewWithTag(newTransitionName);
                        System.out.println("newSharedElement: not null!: " + newSharedElement + " transitionName: " + newTransitionName);
                        if (newSharedElement != null) {
                            System.out.println("newTransitionName: " + newTransitionName);
                            names.clear();
                            names.add(newTransitionName);
                            sharedElements.clear();
                            sharedElements.put(newTransitionName, newSharedElement);
                        }


                    }


                    mTmpReenterState = null;
                } else {
                    System.out.println("mTmpReenterState: NULL!");
                    //if mTmpReenterState is null then activity is exiting. Taken straight from example
                    View navigationBar = findViewById(android.R.id.navigationBarBackground);
                    View statusBar = findViewById(android.R.id.statusBarBackground);
                    System.out.println("navigationBar: " + navigationBar + "  statusBar: " + statusBar);


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

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println("onCreate!!!!!!!!:ArticleListActivity");
        super.onCreate(savedInstanceState);

        getLoaderManager().initLoader(0, null, this);
        setContentView(R.layout.activity_article_list);
        setExitSharedElementCallback(mCallback);


        //mToolbar = (Toolbar) findViewById(R.id.toolbar);

        //final View toolbarContainerView = findViewById(R.id.toolbar_container);

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
            System.out.println("refeshing...");
            refresh();
        }else {
            System.out.println("not refreshing");
        }
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        System.out.println("Starting:ArticleListActivity");

        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));

    }

    @Override
    protected void onPause() {
        System.out.println("Pausing:ArticleListActivity");
        super.onPause();
    }

    @Override
    protected void onStop() {
        System.out.println("Stopping:ArticleListActivity");
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    @Override
    protected void onResume() {
        System.out.println("Resuming:ArticleListActivity");
        super.onResume();

        mIsDetailsActivityStarted = false;
    }

    private boolean loaderRestarting = false;
    int startingPosition;
    int currentPosition;

    @Override
    public void onActivityReenter(int requestCode, Intent data) {
        System.out.println("onActivityReenter:ArticleListActivity");
        super.onActivityReenter(requestCode, data);
        mTmpReenterState = new Bundle(data.getExtras());
        startingPosition = mTmpReenterState.getInt(EXTRA_STARTING_ALBUM_POSITION);
        currentPosition = mTmpReenterState.getInt(EXTRA_CURRENT_ALBUM_POSITION);

        LoaderManager lm = getLoaderManager();
        if (adapter != null) {
            lm.initLoader(0, null, this);
            System.out.println("initLoader");
        }else {
            loaderRestarting = true;
            lm.restartLoader(0, null, this);
            System.out.println("restartLoader");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            System.out.println("postponeEnterTransition");
            postponeEnterTransition();
        }
        //getLoaderManager().initLoader(0, null, this); //too slow
        System.out.println("onActivityReenter:startingPosition: " + startingPosition + " currentPosition: " + currentPosition);
        if (!loaderRestarting && startingPosition != currentPosition) {
            System.out.println("Scrolling to: onACtivityReenter " + currentPosition);
            mRecyclerView.scrollToPosition(currentPosition);

        }



        mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                // TODO: figure out why it is necessary to request layout here in order to get a smooth transition.
                mRecyclerView.requestLayout();


                if(!loaderRestarting) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        System.out.println("OnActivtyReenter: startPostponedEnterTransition");
                        startPostponedEnterTransition();
                    }
                }

                return true;
            }
        });



    }


    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("mRefreshingReceiver:onReceive");
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                System.out.println("updatRefreshingUI");
                updateRefreshingUI();
            }
        }

    };

    private void updateRefreshingUI() {
        System.out.println("updatRefreshingUI?: " + mIsRefreshing);

        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        System.out.println("onCreateLoader:ArticleListActivity");
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        System.out.println("onLoadFinished:ArticleListActivity");
        adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);

        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);

        mRecyclerView.setLayoutManager(sglm);
        mRecyclerView.requestLayout();

        if(loaderRestarting) {
            if (startingPosition != currentPosition) {
                System.out.println("Scrolling to: " + currentPosition);
                mRecyclerView.scrollToPosition(currentPosition);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    System.out.println("startPostPoned:OnLoadFinished");


                    loaderRestarting = false;

                }
            }




        }





    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        System.out.println("onLoaderReset:ArticleListActivity");
        mRecyclerView.setAdapter(null);
    }



    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            System.out.println("newAdapter:ArticleListActivity");
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            System.out.println("onCreateViewHolder:ArticleListActivity");
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);

            final ViewHolder vh = new ViewHolder(view);

            vh.titleView.setTypeface(Typeface.createFromAsset(getResources().getAssets(),"Roboto-Medium.ttf"));


            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            System.out.println("onBindViewHolder:ArticleListActivity");
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
            //animation transition
            System.out.println("onBindViewHolder:position: " + position);
            holder.bind(position,mCursor);
        }

        @Override
        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            System.out.println("##### onAttachedToRecyclerView");
            super.onAttachedToRecyclerView(recyclerView);
        }

        @Override
        public void onViewAttachedToWindow(ViewHolder holder) {
            System.out.println("##### onViewAttachedToWindow");
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
        public DynamicHeightNetworkImageView thumbnailView;

        public TextView titleView;
        public TextView subtitleView;
        private Cursor mCursor;

        private int mAlbumPosition;

        final ImageView mAlbumImage;



        public ViewHolder(View view) {

            super(view);
            System.out.println("ViewHolder():ArticleListActivity");
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
            mAlbumImage = (ImageView) view.findViewById(R.id.thumbnail);
            view.setOnClickListener(this);

        }



        public void bind(int position, Cursor mCursor) {
            this.mCursor = mCursor;

            System.out.println("onCreateViewHolder:mCursor.getPosition(): " + mCursor.getPosition());
            String title =  mCursor.getString(ArticleLoader.Query.TITLE);
           // mAlbumPosition = mCursor.getPosition();
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
                System.out.println("Setting extra position: " + mAlbumPosition);
                newActivity.putExtra(EXTRA_STARTING_ALBUM_POSITION, mAlbumPosition);


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                        System.out.println("VIEWid: " + view.toString());
//                        Pair pairs[] = new Pair[1];
//                        Pair<View,String> pair;
//                        pair = new Pair(view, "image_transition");
//
//                        pairs[0] = pair;
//
//                        ActivityOptionsCompat options = ActivityOptionsCompat.
//                                makeSceneTransitionAnimation(ArticleListActivity.this,pairs);

                    if(!mIsDetailsActivityStarted) {
                        mIsDetailsActivityStarted = true;
                        System.out.println("starting activity with transition bundle");
                        startActivity(newActivity, ActivityOptionsCompat.makeSceneTransitionAnimation(ArticleListActivity.this,
                                mAlbumImage, mAlbumImage.getTransitionName()).toBundle());

                    }

                }
                else {
                    System.out.println("Not Lollipop");
                    startActivity(newActivity);
                }


            }














        }
    }

