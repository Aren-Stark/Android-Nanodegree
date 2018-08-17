package fyi.jackson.drew.rezept.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import fyi.jackson.drew.rezept.R;
import fyi.jackson.drew.rezept.widget.RecipeIngredientsWidget;
import fyi.jackson.drew.rezept.model.Recipe;
import fyi.jackson.drew.rezept.model.Step;
import fyi.jackson.drew.rezept.recycler.InstructionListAdapter;
import fyi.jackson.drew.rezept.recycler.LinePagerIndicatorDecoration;
import fyi.jackson.drew.rezept.recycler.PagerSnapHelper;
import fyi.jackson.drew.rezept.recycler.holder.StepViewHolder;
import fyi.jackson.drew.rezept.ui.PlayerStateChangeEventListener;

public class CookingFragment extends Fragment implements ViewPager.OnPageChangeListener {

    public static final String TAG = CookingFragment.class.getSimpleName();
    public static final String EXTRA_RECIPE_ITEM = "EXTRA_RECIPE_ITEM";
    public static final String EXTRA_TRANSITION_NAME = "EXTRA_TRANSITION_NAME";

    @BindView(R.id.rv_instructions) RecyclerView instructionsRecyclerView;
    private Unbinder unbinder;

    private SimpleExoPlayer exoPlayer;
    private Recipe currentRecipe;
    private int previousActivePosition = 0;

    public CookingFragment() {}

    public static CookingFragment newInstance(Recipe recipe, String transitionName) {
        CookingFragment cookingFragment = new CookingFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_RECIPE_ITEM, recipe);
        bundle.putString(EXTRA_TRANSITION_NAME, transitionName);
        cookingFragment.setArguments(bundle);
        return cookingFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        postponeEnterTransition();
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            setSharedElementEnterTransition(
//                    TransitionInflater.from(getContext())
//                            .inflateTransition(android.R.transition.move));
//        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cooking, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        unbinder = ButterKnife.bind(this, view);

        Recipe recipe = getArguments().getParcelable(EXTRA_RECIPE_ITEM);
        String transitionName = getArguments().getString(EXTRA_TRANSITION_NAME);

        currentRecipe = recipe;

        bindTo(recipe, transitionName);
        broadcastToWidgets(recipe);
    }

    private void bindTo(Recipe recipe, String transitionName) {

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            playerView.setTransitionName(transitionName);
//        }

//        Picasso.get()
//                .load(recipe.getImage())
//                .into(mainImage, new Callback() {
//                    @Override
//                    public void onSuccess() {
//                        startPostponedEnterTransition();
//                    }
//
//                    @Override
//                    public void onError(Exception e) {
//                        startPostponedEnterTransition();
//                    }
//                });

        InstructionListAdapter adapter = new InstructionListAdapter(recipe);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(
                getContext(), LinearLayoutManager.HORIZONTAL, false);
        instructionsRecyclerView.setAdapter(adapter);
        instructionsRecyclerView.setLayoutManager(layoutManager);

        PagerSnapHelper snapHelper = new PagerSnapHelper(this);
        snapHelper.attachToRecyclerView(instructionsRecyclerView);

        instructionsRecyclerView.addItemDecoration(new LinePagerIndicatorDecoration());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        releasePlayer();
        unbinder.unbind();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPlayer();
    }

    @Override
    public void onResume() {
        super.onResume();
        playPlayer();
    }

    private void broadcastToWidgets(Recipe recipe) {
        Log.d(TAG, "broadcastToWidgets: Attempting to send broadcast to RecipeIngredientsWidget");
        Intent widgetIntent = new Intent(getContext(), RecipeIngredientsWidget.class);
        widgetIntent.setAction(RecipeIngredientsWidget.ACTION_UPDATE_WIDGET_RECIPE);
        widgetIntent.putExtra(RecipeIngredientsWidget.EXTRA_RECIPE, recipe);
        getActivity().sendBroadcast(widgetIntent);
    }

    private void initializePlayer(Uri mediaUri) {
        if (exoPlayer == null) {
            TrackSelector trackSelector = new DefaultTrackSelector();
            LoadControl loadControl = new DefaultLoadControl();
            RenderersFactory renderersFactory = new DefaultRenderersFactory(getContext());
            exoPlayer = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, loadControl);
        } else {
            exoPlayer.stop();
        }
        String userAgent = Util.getUserAgent(getContext(), "Rezept");
        MediaSource mediaSource = new LoopingMediaSource(
                new ExtractorMediaSource
                    .Factory(new DefaultDataSourceFactory(getContext(), userAgent))
                    .createMediaSource(mediaUri));
        exoPlayer.prepare(mediaSource);
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.addListener(new PlayerStateChangeEventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (previousActivePosition == 0) return;
                StepViewHolder viewHolder = (StepViewHolder)
                        instructionsRecyclerView.findViewHolderForAdapterPosition(previousActivePosition);
                if (viewHolder == null) return;
                viewHolder.progressBar.setVisibility(
                        (playbackState == Player.STATE_READY) ?
                                View.GONE : View.VISIBLE);
            }
        });
    }

    private void stopPlayer() {
        if (exoPlayer != null) exoPlayer.stop();
    }

    private void playPlayer() {
        if (exoPlayer != null) setupPlayerView(previousActivePosition);
    }

    private void releasePlayer() {
        if (exoPlayer == null) return;
        stopPlayer();
        exoPlayer.release();
        exoPlayer = null;
    }

    private void setupPlayerView(int position) {
        StepViewHolder viewHolder = (StepViewHolder)
                instructionsRecyclerView.findViewHolderForAdapterPosition(position);
        if (viewHolder == null) {
            Log.d(TAG, "setupPlayerView: Null ViewHolder at " + position);
            return;
        }
        int dataPosition = position - 1;
        Step activeStep = currentRecipe.getSteps().get(dataPosition);
        String mediaString = activeStep.getVideoUrl();
        if (mediaString.equals("")) {
            stopPlayer();
        } else {
            initializePlayer(Uri.parse(mediaString));
            viewHolder.playerView.setPlayer(exoPlayer);
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        if (previousActivePosition == position) return;

        // The current page is different than the previous page
        if (position == 0) {
            // ingredients page
            stopPlayer();
        } else {
            // step page
            setupPlayerView(position);
        }

        previousActivePosition = position;
    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }
}
