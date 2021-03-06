package fr.telecomlille.mydrone.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

/** View permettant le traitement de la mat récupérée depuis la BebopVideoView
 * par openCV. Reconnaissance à l'aide de classifiers haar cascade. Cette vue n'affiche
 * qu'un rectangle par dessus les formes reconnues.
 */

public class CVClassifierView extends View {

    private final static String CLASS_NAME = CVClassifierView.class.getSimpleName();
    private final Context ctx;
    private final Object lock = new Object();
    private CascadeClassifier mClassifier;
    private Thread openCVThread = null;
    private BebopVideoView bebopVideoView = null;
    private ImageView cvPreviewView = null;
    private Rect[] facesArray = null;
    private Paint paint;
    private float mX = 0;
    private float mY = 0;

    public CVClassifierView(Context context) {
        this(context, null);
    }

    public CVClassifierView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CVClassifierView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        ctx = context;
        mClassifier = null;

        // objet canvas utilisé pour les rectangles
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
    }

    public void resume(final BebopVideoView bebopVideoView, final ImageView cvPreviewView) {
        if (getVisibility() == View.VISIBLE) {
            this.bebopVideoView = bebopVideoView;
            this.cvPreviewView = cvPreviewView;

            openCVThread = new CascadingThread(ctx);
            openCVThread.start();
        }
    }

    public void pause() {
        if (getVisibility() == View.VISIBLE) {
            openCVThread.interrupt();

            try {
                openCVThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void setClassifier(CascadeClassifier cascade) {
        this.mClassifier = cascade;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Log.d(CLASS_NAME, "onDraw");

        synchronized (lock) {
            if (facesArray != null && facesArray.length > 0) {
                for (Rect target : facesArray) {
                    //Todo: Récupérer les coordonnées et faire le traitement
                    Log.i(CLASS_NAME, "found face size=" + target.area());
                    paint.setColor(Color.GREEN);
                    canvas.drawRect((float) target.tl().x * mX, (float) target.tl().y * mY, (float) target.br().x * mX, (float) target.br().y * mY, paint);
                    facesArray = null;
                }
            }
        }

        super.onDraw(canvas);
    }

    private class CascadingThread extends Thread {
        private final Handler handler;
        boolean interrupted = false;

        private CascadingThread(final Context ctx) {
            handler = new Handler(ctx.getMainLooper());
        }

        public void interrupt() {
            interrupted = true;
        }

        @Override
        public void run() {
            Log.d(CLASS_NAME, "cascadeRunnable");

            final Mat firstMat = new Mat();
            final Mat mat = new Mat();

            while (!interrupted) {
                final Bitmap source = bebopVideoView.getBitmap();

                if (source != null) {
                    Utils.bitmapToMat(source, firstMat);
                    firstMat.assignTo(mat);

                    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);

                    final int minRows = Math.round(mat.rows() * .12f);

                    final Size minSize = new Size(minRows, minRows);
                    final Size maxSize = new Size(0, 0);

                    final MatOfRect faces = new MatOfRect();

                    mClassifier.detectMultiScale(mat, faces);

                    synchronized (lock) {
                        facesArray = faces.toArray();

                        mX = firstMat.width() / mat.width();
                        mY = firstMat.height() / mat.height();

                        faces.release();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                invalidate();
                            }
                        });
                    }
                }

                try {
                    sleep(200);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }

            firstMat.release();
            mat.release();
        }

        private void runOnUiThread(Runnable r) {
            handler.post(r);
        }
    }
}

