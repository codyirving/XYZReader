package com.example.xyzreader.ui;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.ShareCompat;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    public static final String ARG_ITEM_ID = "item_id";
    private static final String TAG = "ArticleDetailFragment";
    private static final String ARG_ALBUM_IMAGE_POSITION = "arg_album_image_position";
    private static final String ARG_STARTING_ALBUM_IMAGE_POSITION = "arg_starting_album_image_position";
    private Cursor mCursor;
    private long mItemId;
    private View mRootView;
    private int mMutedColor = 0xFF333333;
    private ImageView mPhotoView;
    private ImageView mAlbumImage;
    private int mStartingPosition;
    private int mAlbumPosition;
    private boolean mIsTransitioning;
    private long mBackgroundImageFadeMillis;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId, int position, int startingPosition) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        arguments.putInt(ARG_ALBUM_IMAGE_POSITION, position);
        arguments.putInt(ARG_STARTING_ALBUM_IMAGE_POSITION, startingPosition); //for tracking return anim

        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    /**
     * Returns true if {@param view} is contained within {@param container}'s bounds.
     */
    private static boolean isViewInBounds(@NonNull View container, @NonNull View view) {
        Rect containerBounds = new Rect();
        container.getHitRect(containerBounds);
        return view.getLocalVisibleRect(containerBounds);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }
        mStartingPosition = getArguments().getInt(ARG_STARTING_ALBUM_IMAGE_POSITION);
        mAlbumPosition = getArguments().getInt(ARG_ALBUM_IMAGE_POSITION);

        mIsTransitioning = savedInstanceState == null && mStartingPosition == mAlbumPosition;
        mBackgroundImageFadeMillis = 1000;  //TODO: string resource
        setHasOptionsMenu(true);
    }

    public ArticleDetailActivity getActivityCast() {
        return (ArticleDetailActivity) getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        mPhotoView = (ImageView) mRootView.findViewById(R.id.photo);
        mAlbumImage = mPhotoView;
        mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                        .setType("text/plain")
                        .setText("Some sample text")
                        .getIntent(), getString(R.string.action_share)));
            }
        });
        return mRootView;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                getActivityCast().onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);

    }

    private void startPostponedEnterTransition() {
        if (mAlbumPosition == mStartingPosition) {
            mAlbumImage.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    mAlbumImage.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        getActivityCast().startPostponedEnterTransition();
                    }
                    return true;
                }
            });
        }
    }

    /**
     * Returns the shared element that should be transitioned back to the previous Activity,
     * or null if the view is not visible on the screen.
     */
    @Nullable
    ImageView getAlbumImage() {
        if (isViewInBounds(getActivity().getWindow().getDecorView(), mAlbumImage)) {
            return mAlbumImage;
        }
        return null;
    }

    private void bindViews() {
        if (mRootView == null) {
            return;
        }
        TextView bylineView = (TextView) mRootView.findViewById(R.id.article_byline);
        TextView bodyView = (TextView) mRootView.findViewById(R.id.article_body);
        bodyView.setTypeface(Typeface.createFromAsset(getResources().getAssets(), "Roboto-Regular.ttf"));

        if (mCursor != null) {
            mRootView.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mAlbumImage.setTransitionName(mCursor.getString(ArticleLoader.Query.TITLE));
            }
            final Toolbar toolbar = (Toolbar) mRootView.findViewById(R.id.meta_bar);
//            toolbar.setSubtitle(Html.fromHtml(
//                    DateUtils.getRelativeTimeSpanString(
//                            mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
//                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
//                            DateUtils.FORMAT_ABBREV_ALL).toString()
//                            + " by <font color='#ffffff'>"
//                            + mCursor.getString(ArticleLoader.Query.AUTHOR)
//                            + "</font>"));
            getActivityCast().setSupportActionBar(toolbar);
            getActivityCast().getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            CollapsingToolbarLayout collapsingToolbarLayout = (CollapsingToolbarLayout) mRootView.findViewById(R.id.photo_container);

            collapsingToolbarLayout.setExpandedTitleTextAppearance(R.style.AppBarText);
            collapsingToolbarLayout.setExpandedTitleTypeface(Typeface.createFromAsset(getResources().getAssets(), "YanoneKaffeesatz-Bold.ttf"));
            collapsingToolbarLayout.setCollapsedTitleTypeface(Typeface.createFromAsset(getResources().getAssets(), "YanoneKaffeesatz-Bold.ttf"));
            collapsingToolbarLayout.setTitle(mCursor.getString(ArticleLoader.Query.TITLE));
            bylineView.setText(Html.fromHtml(
                    DateUtils.getRelativeTimeSpanString(
                            mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString()
                            + " by <font color='#ffffff'>"
                            + mCursor.getString(ArticleLoader.Query.AUTHOR)
                            + "</font>"));
            bodyView.setText(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY)));
            ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                    .get(mCursor.getString(ArticleLoader.Query.PHOTO_URL), new ImageLoader.ImageListener() {
                        @Override
                        public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                            Bitmap bitmap = imageContainer.getBitmap();
                            if (bitmap != null) {
                                Palette p = Palette.generate(bitmap, 12);
                                mMutedColor = p.getDarkVibrantColor(0xFF333333);
                                mPhotoView.setImageBitmap(imageContainer.getBitmap());
                                ColorDrawable colorDrawable = new ColorDrawable(mMutedColor);
                                mRootView.findViewById(R.id.photo_container).setBackgroundColor(mMutedColor);
                                ((CollapsingToolbarLayout) mRootView.findViewById(R.id.photo_container)).setContentScrim(colorDrawable);

                            }
                        }

                        @Override
                        public void onErrorResponse(VolleyError volleyError) {

                        }
                    });
        } else {
            mRootView.setVisibility(View.GONE);

            bylineView.setText("N/A");
            bodyView.setText("N/A");
        }
    }


    public void setStatusBar() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getActivityCast().getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(mMutedColor);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startPostponedEnterTransition();
        }
        bindViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }
}
