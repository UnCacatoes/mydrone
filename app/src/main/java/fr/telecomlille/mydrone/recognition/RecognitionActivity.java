package fr.telecomlille.mydrone.recognition;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageButton;
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
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import fr.telecomlille.mydrone.MainActivity;
import fr.telecomlille.mydrone.R;
import fr.telecomlille.mydrone.drone.BebopDrone;
import fr.telecomlille.mydrone.view.BebopVideoView;
import fr.telecomlille.mydrone.view.CVClassifierView;

public class RecognitionActivity extends AppCompatActivity implements BebopDrone.Listener {

    private final static String CLASS_NAME = RecognitionActivity.class.getSimpleName();

    private BebopVideoView mVideoView;
    private CVClassifierView cvView;
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
            Log.e(CLASS_NAME, "onCreate: failed to initialize OpenCV");
        }
    }

    /**
     * Copie le fichiers XML contenant les instructions de reconnaissance de visage
     * dans les fichiers temporaires, puis le charge avec le CascadeClassifier.
     */
    //Todo: Refactoriser dans CVClassifierView
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
            // L'appel load est nécessaire à cause d'un bug d'OpenCV dans cette version
            mClassifier.load(mCascadeFile.getAbsolutePath());

            cvView.setClassifier(mClassifier);
            cvView.resume(mVideoView, null);
            Log.d(CLASS_NAME, "Classifier has been loaded !");
            if (mClassifier.empty()) {
                Log.e(CLASS_NAME, "Error while loading classifier file.");
            } else {
                Log.d(CLASS_NAME, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
            }
        } catch (IOException e) {
            Log.e("MyActivity", "Failed to load cascade.", e);
        }
    }

    private void initIHM() {
        mVideoView = (BebopVideoView) findViewById(R.id.videoView);
        cvView = (CVClassifierView) findViewById(R.id.cvView);

        mVideoView.setSurfaceTextureListener(mVideoView);

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
        Toast.makeText(this, "C'EST LOAD !", Toast.LENGTH_LONG).show();
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
        Log.d(CLASS_NAME, "onPilotingStateChanged() called with: state = [" + state + "]");
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

    /**
     * Affichage des frames à l'aide de la BebopVideoView
     **/
    @Override
    public void onFrameReceived(ARFrame frame) {
        mVideoView.displayFrame(frame);
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

    //Todo: Ajouter contrôles de base (fleches) pour faciliter débug
}

