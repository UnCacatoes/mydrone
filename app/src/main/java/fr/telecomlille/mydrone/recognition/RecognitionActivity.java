package fr.telecomlille.mydrone.recognition;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARControllerCodec;
import com.parrot.arsdk.arcontroller.ARFrame;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import fr.telecomlille.mydrone.MainActivity;
import fr.telecomlille.mydrone.R;
import fr.telecomlille.mydrone.drone.BebopDrone;
import fr.telecomlille.mydrone.view.BebopVideoView;

public class RecognitionActivity extends AppCompatActivity implements BebopDrone.Listener {

    private static final String TAG = "RecognitionActivity";

    private BebopVideoView mVideoView;
    private int mScreenWidth, mScreenHeight;
    private BebopDrone mDrone;
    private ProgressDialog mConnectionDialog;
    private ImageButton mTakeoffLandButton;
    private CascadeClassifier mClassifier;
    private final BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case BaseLoaderCallback.SUCCESS:
                    loadCascade();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };
    private boolean mIsEnabled = false;
    private SurfaceView canvasView;
    private MatOfRect faces;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);
        initIHM();

        mScreenHeight = mVideoView.getHeight();
        mScreenWidth = mVideoView.getWidth();

        ARDiscoveryDeviceService deviceService = getIntent()
                .getParcelableExtra(MainActivity.EXTRA_DEVICE_SERVICE);

        if (deviceService == null) {
            throw new IllegalStateException("Calling Activity must start this Activity with an " +
                    "ARDiscoveryDeviceService object passed as an extra (EXTRA_DEVICE_SERVICE).");
        }

        mDrone = new BebopDrone(this, deviceService);
        mDrone.addListener(this);

        // Démarre le chargement d'OpenCV
        if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, this, mLoaderCallback)) {
            Log.e(TAG, "onCreate: failed to initialize OpenCV");
        }

        //Run thread
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Starting face detection thread");
                while (true) {
                    if(mVideoView!=null) {
                        onBitmapReceived(mVideoView.getBitmap());
                    }
                }
            }
        });
        thread.start();
    }

    /**
     * Copie le fichiers XML contenant les instructions de reconnaissance de visage
     * dans les fichiers temporaires, puis le charge avec le CascadeClassifier.
     */
    private void loadCascade() {
        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            mClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            mClassifier.load(mCascadeFile.getAbsolutePath());
            if (mClassifier.empty()) {
                Log.e(TAG, "Error while loading classifier file.");
            } else {
                Log.d(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
            }
        } catch (IOException e) {
            Log.e("MyActivity", "Failed to load cascade.", e);
        }
    }

    private void initIHM() {

        mVideoView = (BebopVideoView) findViewById(R.id.videoView);
        mTakeoffLandButton = (ImageButton) findViewById(R.id.btn_takeoff_land);
        mTakeoffLandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (mDrone.getFlyingState()) {
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                        mDrone.takeOff();
                        break;
                    // Atterir directement après le décollage ?
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_TAKINGOFF:
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                    case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                        mDrone.land();
                        break;
                }
            }
        });
        findViewById(R.id.btn_emergency).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDrone.emergency();
            }
        });

        // Monter en altitude
        findViewById(R.id.btn_gaz_up).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        mDrone.setGaz(50);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        mDrone.setGaz(0);
                        break;
                }
                return true;
            }
        });
        // Descendre en altitude
        findViewById(R.id.btn_gaz_down).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        mDrone.setGaz(-50);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        mDrone.setGaz(0);
                        break;
                }
                return true;
            }
        });
        // Pivoter sur la droite
        findViewById(R.id.btn_yaw_right).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        mDrone.setYaw(50);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        mDrone.setYaw(0);
                        break;
                }
                return true;
            }
        });
        // Pivoter sur la gauche
        findViewById(R.id.btn_yaw_left).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        mDrone.setYaw(-50);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        mDrone.setYaw(0);
                        break;
                }
                return true;
            }
        });
        // Avancer
        findViewById(R.id.btn_forward).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        mDrone.setPitch(50);
                        mDrone.setFlag(BebopDrone.FLAG_ENABLED);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        mDrone.setPitch(0);
                        mDrone.setFlag(BebopDrone.FLAG_DISABLED);
                        break;
                }
                return true;
            }
        });
        // Reculer
        findViewById(R.id.btn_back).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        mDrone.setPitch(-50);
                        mDrone.setFlag(BebopDrone.FLAG_ENABLED);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        mDrone.setPitch(0);
                        mDrone.setFlag(BebopDrone.FLAG_DISABLED);
                        break;
                }
                return true;
            }
        });
        // Aller à droite
        findViewById(R.id.btn_roll_right).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        mDrone.setRoll(50);
                        mDrone.setFlag(BebopDrone.FLAG_ENABLED);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        mDrone.setRoll(0);
                        mDrone.setFlag(BebopDrone.FLAG_DISABLED);
                        break;
                }
                return true;
            }
        });
        // Aller à gauche
        findViewById(R.id.btn_roll_left).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        mDrone.setRoll(-50);
                        mDrone.setFlag(BebopDrone.FLAG_ENABLED);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        mDrone.setRoll(0);
                        mDrone.setFlag(BebopDrone.FLAG_DISABLED);
                        break;
                }
                return true;
            }
        });

        canvasView = (SurfaceView)findViewById(R.id.canvasView);
        canvasView.setZOrderOnTop(true);
        canvasView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        mVideoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                onCanvasViewClick(view,motionEvent);
                return false;
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mDrone != null && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING
                .equals(mDrone.getConnectionState()))) {
            mConnectionDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionDialog.setIndeterminate(true);
            mConnectionDialog.setMessage(getString(R.string.connecting));
            mConnectionDialog.setCancelable(false);
            mConnectionDialog.show();

            if (!mDrone.connect()) {
                Toast.makeText(this, R.string.error_connecting, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mDrone != null) {
            mConnectionDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionDialog.setIndeterminate(true);
            mConnectionDialog.setMessage(getString(R.string.disconnecting));
            mConnectionDialog.setCancelable(false);
            mConnectionDialog.show();

            mDrone.land();

            if (!mDrone.disconnect()) {
                Toast.makeText(this, R.string.error_disconnecting, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        mDrone.dispose();
        super.onDestroy();
    }


    @Override
    public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
        switch (state) {
            case ARCONTROLLER_DEVICE_STATE_RUNNING:
                mConnectionDialog.dismiss();
                break;
            case ARCONTROLLER_DEVICE_STATE_STOPPED:
                // Si la déconnexion est un succès, retour à l'activité précédente
                mConnectionDialog.dismiss();
                finish();
        }
    }

    @Override
    public void onBatteryChargeChanged(int batteryPercentage) {

    }

    @Override
    public void onPilotingStateChanged(ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
        Log.d(TAG, "onPilotingStateChanged() called with: state = [" + state + "]");
        switch (state) {
            case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                mTakeoffLandButton.setImageLevel(0);
                break;
            case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
            case ARCOMMANDS_ARDRONE3_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                mTakeoffLandButton.setImageLevel(1);
                break;
        }
    }

    @Override
    public void onPictureTaken(ARCOMMANDS_ARDRONE3_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
    }

    @Override
    public void configureDecoder(ARControllerCodec codec) {
        mVideoView.configureDecoder(codec);
    }

    @Override
    public void onFrameReceived(ARFrame frame) {
        mVideoView.displayFrame(frame);
        onImageReceived(frame);
    }

    @Override
    public void onMatchingMediasFound(int nbMedias) {

    }

    @Override
    public void onDownloadProgressed(String mediaName, int progress) {

    }

    @Override
    public void onDownloadComplete(String mediaName) {

    }

    @Override
    public void onRelativeMoveFinished(float dX, float dY, float dZ, float dPsi, boolean isInterrupted) {

    }


    public void enableFollowing(boolean enable) {
        mIsEnabled = enable;
        if (!enable) {
            mDrone.setFlag(BebopDrone.FLAG_DISABLED);
            mDrone.setRoll(0);
            mDrone.setPitch(0);
            mDrone.setYaw(0);
        }
    }

    public void onImageReceived(ARFrame frame) {

    }

    private void onBitmapReceived(Bitmap bmp){
        if (true) {
            if (bmp == null) {
                Log.v(TAG, "onImageReceived: cant decode.");
                return;
            }
            Log.i(TAG,"Bitmap size : " + Integer.toString(bmp.getRowBytes()));
            Mat firstMat = new Mat();
            Mat mat = new Mat();
            firstMat.assignTo(mat);

            //Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);

            final int minRows = Math.round(mat.rows() * .12f);

            final Size minSize = new Size(minRows, minRows);
            final Size maxSize = new Size(0, 0);

            final MatOfRect faces = new MatOfRect();

            mClassifier.detectMultiScale(mat, faces);

            if (faces.size().width != 0 && faces.size().height != 0) {
                onFaceDetection(mat,faces);
            }
        }
    }

    private void onFaceDetection(Mat image, MatOfRect faces){

        Log.d(TAG, "onImageReceived: face recognized !");
        drawFacesOnSurface(faces.toArray());
        Rect faceConsidered = faces.toArray()[0];
        //Affichage du rectangle
        //opencv_imgproc.rectangle(image, faceConsidered, new opencv_core.Scalar(0, 255, 0, 1));
        int[] faceCenterCoordinates;
        if (image.size().width != mVideoView.getWidth() || image.size().height != mVideoView.getHeight()) {
            faceCenterCoordinates = new int[]{faceConsidered.x + (faceConsidered.width / 2), faceConsidered.y + (faceConsidered.height/ 2)};
        } else {
            faceCenterCoordinates = new int[]{((int) (faceConsidered.x * mVideoView.getWidth() / image.size().width)),
                    ((int) (faceConsidered.y * mVideoView.getHeight() / image.size().height))};
        }
        //image.size().height();
        mDrone.setFlag(BebopDrone.FLAG_ENABLED);
        mDrone.setRoll(((10 * (faceCenterCoordinates[0] - mScreenWidth / 2) / Math.abs(faceCenterCoordinates[0] - mScreenWidth / 2))));
        mDrone.setGaz(((10 * (mScreenHeight / 2 - faceCenterCoordinates[1]) / Math.abs(mScreenHeight / 2 - faceCenterCoordinates[1]))));

        if ((faceCenterCoordinates[0] < mScreenWidth / 2 + 10) && (faceCenterCoordinates[0] > mScreenWidth / 2 - 10)) {
            mDrone.setRoll(0);
        }
        if ((faceCenterCoordinates[1] < mScreenHeight / 2 + 10) && (faceCenterCoordinates[1] > mScreenHeight / 2 - 10)) {
            mDrone.setGaz(0);
        }
    }

    private void drawFacesOnSurface(Rect[] faces) {
        Log.i(TAG,"Face(s) detected");
        SurfaceHolder holder = canvasView.getHolder();
        Canvas canvas = holder.lockCanvas();
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        for(Rect face : faces){
            canvas.drawRect(face.x,face.y,face.x+face.width,face.y+face.height,paint);
        }
        holder.unlockCanvasAndPost(canvas);
    }

    private void onCanvasViewClick(View view, MotionEvent event) {
        if (!(faces == null) && !faces.empty()) {
            for (Rect face : faces.toArray()) {
                if (event.getX() >= face.x && event.getX() <= (face.x + face.width) && event.getY() >= face.y && event.getY() <= (face.y + face.height)) {
                    TextView followingStatus = (TextView) findViewById(R.id.txt_following_status);
                    followingStatus.setText("Following target");
                }
            }
        }
    }
}
