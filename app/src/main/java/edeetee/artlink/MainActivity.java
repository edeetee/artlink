package edeetee.artlink;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.IdRes;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.CardView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.flurgle.camerakit.CameraListener;
import com.flurgle.camerakit.CameraView;

import org.opencv.android.Utils;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.ORB;


import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

import static org.opencv.core.Core.NORM_HAMMING;
import static org.opencv.core.Core.min;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    @BindView(R.id.camera) CameraView cameraView;
    @BindView(R.id.title) TextView title;
    @BindView(R.id.match) TextView matchText;
    @BindView(R.id.activity_main) CoordinatorLayout layout;
    @BindView(R.id.draw_dots) DrawDotsView drawDots;
    @BindView(R.id.bottom_card) CardView bottomCard;
    @BindView(R.id.nestedScroll) NestedScrollView scrollView;

    //TODO make the bottom card work well etc

    static{
        try{
            System.loadLibrary("opencv_java3");
        }catch (Exception e){
            Log.e(TAG, "opencv load failed", e);
        }
    }

    List<Mat> descriptors = new ArrayList<>();

    Handler processImage;
    HandlerThread processThread = new HandlerThread("ProcessThread");
    Handler mainHandler = new Handler();

    @IdRes int[] images = new int[]{
            R.drawable.flask_front,
            R.drawable.box
    };

    String[] titles = new String[]{
            "Flask (front)",
            "Box"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        //start the processing thread

        Mat photoToProcess = new Mat();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        for (int image : images) {
            //read drawable
            Drawable d = ContextCompat.getDrawable(this, image);
            Bitmap bitmap = ((BitmapDrawable)d).getBitmap();
            Utils.bitmapToMat(bitmap, photoToProcess);

            //get descriptor
            descriptors.add(getDescriptors(photoToProcess));
        }

        final BottomSheetBehavior behavior = BottomSheetBehavior.from(scrollView);
        title.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                behavior.setPeekHeight(title.getHeight());
            }
        });

        processThread.start();
        processImage = new Handler(processThread.getLooper());

        cameraView.setCameraListener(new CameraListener() {
            @Override
            public void onCameraOpened() {
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        cameraView.captureImage();
                    }
                }, 1000);
            }

            @Override
            public void onPictureTaken(final byte[] jpeg) {
                processImage.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        processPhoto(jpeg);
                    }
                }, 1000);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    @Override
    protected void onPause() {
        cameraView.stop();
        super.onPause();
    }

    private static final float matchRatio = 0.75f;
    private static final float minMatchesRatio = 0.00f;

    Mat photoToProcess = new Mat();
    Mat processedDescriptors = new Mat();
    MatOfKeyPoint processedKeyPoints = new MatOfKeyPoint();
    //BFMatcher matcher = new BFMatcher(NORM_HAMMING, true);
    DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE);

    protected void processPhoto(byte[] jpeg){
        Bitmap img = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);

        //image to mat
        Utils.bitmapToMat(img, photoToProcess);

        //get key points
        detectAndCompute(photoToProcess, processedKeyPoints, processedDescriptors);

        //find closest match
        float bestMatchRatio = 0;
        List<DMatch> bestMatchesList = null;
        int bestMatchI = 0;

        for (int i = 0; i < descriptors.size(); i++) {
            Mat descriptor = descriptors.get(i);

            //process knn of key points
            if(processedDescriptors.type() != descriptor.type() || processedDescriptors.cols() != descriptor.cols())
                continue;
            List<MatOfDMatch> matches = new ArrayList<>();
            matcher.knnMatch(processedDescriptors, descriptor, matches, 2);

            List<DMatch> correctMatches = new ArrayList<>();

            //amount of matching points
            for (MatOfDMatch match : matches) {
                DMatch[] matchArray = match.toArray();
                if(matchArray.length == 2 && matchArray[0].distance < (matchRatio*matchArray[1].distance))
                    correctMatches.add(matchArray[0]);
                else
                    correctMatches.add(matchArray[0]);
            }

            float matchRatio = (float)correctMatches.size()/descriptor.total();
            //float matchRatio = (float)matches.total()/descriptor.total();
            //is new best
            if(bestMatchRatio < matchRatio){
                bestMatchRatio = matchRatio;
                bestMatchesList = correctMatches;
                bestMatchI = i;
            }
        }


        //if found a match, draw name
        final String titleText = (bestMatchesList != null && minMatchesRatio < bestMatchRatio) ?
                titles[bestMatchI]:
            "NONE";

        final String matchString = (bestMatchesList != null && minMatchesRatio < bestMatchRatio) ?
                bestMatchRatio*100 + "% match" :
                "0% match";

        KeyPoint[] processedKeyPointsArray = processedKeyPoints.toArray();
        drawDots.points.clear();
        if(bestMatchesList != null && minMatchesRatio < bestMatchRatio){
            for (DMatch dMatch : bestMatchesList) {
                drawDots.points.add(pointCVToPointAndroid(processedKeyPointsArray[dMatch.queryIdx].pt));
            }
        } else{
            for (KeyPoint keyPoint : processedKeyPointsArray) {
                drawDots.points.add(pointCVToPointAndroid(keyPoint.pt));
            }
        }

        //loop back
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                title.setText(titleText);
                matchText.setText(matchString);
                drawDots.invalidate();
                cameraView.captureImage();
            }
        });
    }

    protected Mat getDescriptors(Mat img){
        MatOfKeyPoint keyPoints = new MatOfKeyPoint();
        Mat descriptors = new Mat();
        //CALCULATION
        detectAndCompute(img, keyPoints, descriptors);

        return descriptors;
    }

    FeatureDetector detector = FeatureDetector.create(FeatureDetector.ORB);
    DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
    //ORB orb = ORB.create();

    protected void detectAndCompute(Mat image, MatOfKeyPoint keypoints, Mat descriptors){
        //orb.detectAndCompute(image, new Mat(), keypoints, descriptors);
        detector.detect(image, keypoints);
        extractor.compute(image, keypoints, descriptors);
    }

    protected Point pointCVToPointAndroid(org.opencv.core.Point cvPoint){
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        return new Point((int)cvPoint.y, metrics.heightPixels-title.getHeight()-(int)cvPoint.x);
    }
}
