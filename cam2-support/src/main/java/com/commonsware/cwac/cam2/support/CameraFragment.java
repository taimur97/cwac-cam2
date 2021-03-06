/**
 * Copyright (c) 2015 CommonsWare, LLC
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.commonsware.cwac.cam2.support;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import com.commonsware.cwac.cam2.CameraController;
import com.commonsware.cwac.cam2.CameraEngine;
import com.commonsware.cwac.cam2.CameraView;
import com.commonsware.cwac.cam2.PictureTransaction;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import de.greenrobot.event.EventBus;

/**
 * Fragment for displaying a camera preview, with hooks to allow
 * you (or the user) to take a picture.
 */
public class CameraFragment extends Fragment {
  private static final String ARG_OUTPUT="output";
  private CameraController ctlr;
  private ViewGroup previewStack;
  private FloatingActionButton fabSwitch;
  private View progress;

  public static CameraFragment newInstance(Uri output) {
    CameraFragment f=new CameraFragment();
    Bundle args=new Bundle();

    args.putParcelable(ARG_OUTPUT, output);
    f.setArguments(args);

    return(f);
  }

  /**
   * Standard fragment entry point.
   *
   * @param savedInstanceState State of a previous instance
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setRetainInstance(true);
  }

  /**
   * Standard lifecycle method, for when the fragment moves into
   * the started state. Passed along to the CameraController.
   */
  @Override
  public void onStart() {
    super.onStart();

    EventBus.getDefault().register(this);

    if (ctlr!=null) {
      ctlr.start();
    }
  }

  @Override
  public void onHiddenChanged(boolean isHidden) {
    super.onHiddenChanged(isHidden);

    if (!isHidden) {
      ActionBar ab=getActivity().getActionBar();

      ab.setBackgroundDrawable(getActivity()
          .getResources()
          .getDrawable(R.drawable.cwac_cam2_action_bar_bg_transparent));
      ab.setTitle("");

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        ab.setDisplayHomeAsUpEnabled(false);
      }
      else {
        ab.setDisplayShowHomeEnabled(false);
        ab.setHomeButtonEnabled(false);
      }
    }
  }

  /**
   * Standard lifecycle method, for when the fragment moves into
   * the stopped state. Passed along to the CameraController.
   */
  @Override
  public void onStop() {
    if (ctlr!=null) {
      ctlr.stop();
    }

    EventBus.getDefault().unregister(this);

    super.onStop();
  }

  /**
   * Standard lifecycle method, for when the fragment is utterly,
   * ruthlessly destroyed. Passed along to the CameraController,
   * because why should the fragment have all the fun?
   */
  @Override
  public void onDestroy() {
    if (ctlr!=null) {
      ctlr.destroy();
    }

    super.onDestroy();
  }

  /**
   * Standard callback method to create the UI managed by
   * this fragment.
   *
   * @param inflater Used to inflate layouts
   * @param container Parent of the fragment's UI (eventually)
   * @param savedInstanceState State of a previous instance
   * @return the UI being managed by this fragment
   */
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View v=inflater.inflate(R.layout.cwac_cam2_fragment, container, false);

    previewStack=(ViewGroup)v.findViewById(R.id.cwac_cam2_preview_stack);
    progress=v.findViewById(R.id.cwac_cam2_progress);

    FloatingActionButton fab=(FloatingActionButton)v.findViewById(R.id.cwac_cam2_picture);

    fab.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Uri output=getArguments().getParcelable(ARG_OUTPUT);

        PictureTransaction.Builder b=new PictureTransaction.Builder();

        if (output!=null) {
          b.toUri(getActivity(), output);
        }

        ctlr.takePicture(b.build());
      }
    });

    fabSwitch=(FloatingActionButton)v.findViewById(R.id.cwac_cam2_switch_camera);
    fabSwitch.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        progress.setVisibility(View.VISIBLE);
        fabSwitch.setEnabled(false);
        ctlr.switchCamera();
      }
    });

    changeMenuIconAnimation((FloatingActionMenu)v.findViewById(R.id.cwac_cam2_settings));

    onHiddenChanged(false); // hack, since this does not get
                            // called on initial display

    if (ctlr!=null && ctlr.getNumberOfCameras()>0) {
      prepController();
    }

    return(v);
  }

  /**
   * @return the CameraController this fragment delegates to
   */
  public CameraController getController() {
    return(ctlr);
  }

  /**
   * Establishes the controller that this fragment delegates to
   *
   * @param ctlr the controller that this fragment delegates to
   */
  public void setController(CameraController ctlr) {
    this.ctlr=ctlr;
  }

  @SuppressWarnings("unused")
  public void onEventMainThread(CameraController.ControllerReadyEvent event) {
    if (event.isEventForController(ctlr)) {
      prepController();
    }
  }

  private void prepController() {
    LinkedList<CameraView> cameraViews=new LinkedList<CameraView>();
    CameraView cv=(CameraView)previewStack.getChildAt(0);

    cameraViews.add(cv);

    for (int i=1; i < ctlr.getNumberOfCameras(); i++) {
      cv=new CameraView(getActivity());
      cv.setVisibility(View.INVISIBLE);
      previewStack.addView(cv);
      cameraViews.add(cv);
    }

    ctlr.setCameraViews(cameraViews);
  }

  @SuppressWarnings("unused")
  public void onEventMainThread(CameraEngine.OpenedEvent event) {
    progress.setVisibility(View.GONE);
    fabSwitch.setEnabled(true);
  }

  // based on https://goo.gl/3IUM8K

  private void changeMenuIconAnimation(final FloatingActionMenu menu) {
    AnimatorSet set=new AnimatorSet();
    final ImageView v=menu.getMenuIconView();
    ObjectAnimator scaleOutX=ObjectAnimator.ofFloat(v, "scaleX", 1.0f, 0.2f);
    ObjectAnimator scaleOutY=ObjectAnimator.ofFloat(v, "scaleY", 1.0f, 0.2f);
    ObjectAnimator scaleInX=ObjectAnimator.ofFloat(v, "scaleX", 0.2f, 1.0f);
    ObjectAnimator scaleInY=ObjectAnimator.ofFloat(v, "scaleY", 0.2f, 1.0f);

    scaleOutX.setDuration(50);
    scaleOutY.setDuration(50);

    scaleInX.setDuration(150);
    scaleInY.setDuration(150);
    scaleInX.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationStart(Animator animation) {
        v.setImageResource(menu.isOpened()
            ? R.drawable.cwac_cam2_ic_close
            : R.drawable.cwac_cam2_ic_action_settings);
      }
    });

    set.play(scaleOutX).with(scaleOutY);
    set.play(scaleInX).with(scaleInY).after(scaleOutX);
    set.setInterpolator(new OvershootInterpolator(2));
    menu.setIconToggleAnimatorSet(set);
  }
}
