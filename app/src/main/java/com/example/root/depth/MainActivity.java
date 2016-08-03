package com.example.root.depth;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.*;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import org.opencv.android.Utils;

public class MainActivity extends AppCompatActivity {

    ImageView iv = null;
    static Uri uri = null;
    static int[] th = {14, 0, 0};
    static int lab = 0;
    static Mat smat, hmat, rmat, cmat, bmat, resultmat;
    static boolean isLabel[][];
    static int objCluster1[][],objCluster[][];
    double truedepth[][];
    ProgressBar myProgressBar;
    TextView myTextView;
    int myProgress = 0;
    int th1 = 500;
    int count = 0;
    double resd = 0;
    static ArrayList<ArrayList<ArrayList<Integer>>> all = new ArrayList<ArrayList<ArrayList<Integer>>>();
    Bitmap bmap = null, bmap1 = null,bmapaddred = null;
    static String realpath;
    ArrayList<ArrayList<Integer>> avpix = new ArrayList<ArrayList<Integer>>();

    ArrayList<ArrayList<Integer>> bound = new ArrayList<ArrayList<Integer>>();
    ArrayList<Double> avdep = new ArrayList<Double>();
    ArrayList<ArrayList<ArrayList<Integer>>> objnum = new ArrayList<ArrayList<ArrayList<Integer>>>();
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button b = (Button) this.findViewById(R.id.buttonObj);
        Button buttonCamera = (Button)findViewById(R.id.camera);
        myProgressBar = (ProgressBar) findViewById(R.id.progressbar);
        myProgressBar.setProgress(myProgress);
        myProgressBar.setVisibility(View.INVISIBLE);


        myTextView = (TextView)findViewById(R.id.mytextview);

        myTextView.setText("");
        myTextView.setTextColor(Color.BLACK);
        myTextView.setTextSize(40);
        myTextView.setVisibility(View.INVISIBLE);

        buttonCamera.setOnClickListener(new Button.OnClickListener(){
            public void onClick(View v) {
                //使用Intent調用其他服務幫忙拍照
                Intent intent_camera = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

                File f = new File(Environment.getExternalStorageDirectory()+"/DCIM/cameraTmp.jpg");
                try
                {
                    f.createNewFile();
                }
                catch (IOException ex){}

                uri = Uri.fromFile(f);
                intent_camera.putExtra(MediaStore.EXTRA_OUTPUT,uri);
                startActivityForResult(intent_camera,1);

            }

        });


        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                // TODO Auto-generated method stub

                // 建立 "選擇檔案 Action" 的 Intent
                Intent intent = new Intent(Intent.ACTION_PICK);

                // 過濾檔案格式
                intent.setType("image/*");

                // 建立 "檔案選擇器" 的 Intent  (第二個參數: 選擇器的標題)
                Intent destIntent = Intent.createChooser(intent, "Select Photo");

                // 切換到檔案選擇器 (它的處理結果, 會觸發 onActivityResult 事件)
                startActivityForResult(destIntent, 0);
            }
        });





        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }



double reDis(int x, int y, int x1, int y1){
    return Math.sqrt(Math.pow(y - y1, 2) + Math.pow(x - x1, 2));

}
    int findMinDisPoint(int x, int y){
      for(int i = 0; i< objnum.size();i++){

          for(int j = 0; j< objnum.get(i).size(); j++){
              if(objnum.get(i).get(j).get(1) == x && objnum.get(i).get(j).get(0) == y){
                  return i;
              }

          }
      }

        return -1;
        }



    Handler myHandle = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:  //Initial Progress"
                    myProgressBar.setVisibility(View.VISIBLE);
                    myProgress = 0;
                    myProgressBar.setProgress(myProgress);
                    break;
                case 1:  //Upgrade Progress"
                    myProgress += 10;
                    myProgressBar.setProgress(myProgress);
                    break;
                case 2:  //Hide Progress
                    myProgress = 0;
                    myProgressBar.setVisibility(View.INVISIBLE);
                    break;
                case 3:
                    iv.setImageBitmap(bmapaddred);
                    myProgressBar.setVisibility(View.INVISIBLE);
                    break;
                case 4:
                    iv.setImageBitmap(bmap1);
                    myProgressBar.setVisibility(View.INVISIBLE);
                    break;
                case 5:
                    myTextView.setVisibility(View.INVISIBLE);
                    break;
                case 6:
                    myTextView.setVisibility(View.VISIBLE);
                    break;
                case 7:
                    myTextView.setText("Depth: "+String.format("%.3g%n", resd) + "m");
                    break;
            }
        }
    };


    @Override
    public void onResume() {
        super.onResume();
        overridePendingTransition(0, 0);
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);


    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {


                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);
        // 取得檔案的 Uri
        if(requestCode==0) {
            uri = data.getData();
            realpath = getRealPathFromURI(this.getApplicationContext(), uri);
        }
        else if(requestCode==1) {
            realpath = uri.getPath();
        }

        if (uri != null) {

            // 利用 Uri 顯示 ImageView 圖片
            iv = (ImageView) this.findViewById(R.id.imageViewObj);
            iv.setImageURI(uri);
            System.out.println(iv.getImageMatrix());
            iv.setScaleType(ImageView.ScaleType.FIT_START );
            setTitle(uri.toString());

            smat =  new Mat() ;
            hmat =  new Mat() ;
            rmat =  new Mat() ;
            cmat =  new Mat() ;
            bmat =  new Mat() ;
            resultmat =  new Mat() ;
            isLabel = null ;
            objCluster1 = null ;
            objCluster = null ;
            truedepth = null ;

            th1 = 500;
            count = 0;
            resd = 0;
            all.removeAll(all);
            bmap = null;
            bmap1 = null;
            bmapaddred = null;
            avpix.removeAll(avpix);
            bound.removeAll(bound);
            avdep.removeAll(avdep);
            objnum.removeAll(objnum);

            myTextView.setVisibility(View.INVISIBLE);
            myTextView.setText("");


            new Thread(new Runnable() {
                @Override
                public void run() {



                    String name = realpath;
                    Message msg = new Message();
                    msg.what = 0;
                    myHandle.sendMessage(msg);

                    lab = 0;
                    System.out.println("start0");
                    Mat fsmat = Imgcodecs.imread(name);
                    smat = new Mat();

                    Imgproc.resize(fsmat, smat, new Size(fsmat.cols() / 10, fsmat.rows() / 10));
                    System.out.println("start1");


                    cmat = smat.clone();
                    hmat = smat.clone();
                    bmat = smat.clone();
                    Imgproc.bilateralFilter(smat, bmat ,-1,30, 30);
                    msg = new Message();
                    msg.what = 1;
                    myHandle.sendMessage(msg);
                    //canny start

                    Imgproc.cvtColor(smat, cmat, Imgproc.COLOR_BGR2GRAY);

                    Imgproc.blur(cmat, cmat, new Size(3, 3));
                    Imgproc.Canny(cmat, cmat, 80, 180);
                    msg = new Message();
                    msg.what = 1;
                    myHandle.sendMessage(msg);
                    //canny over

		 /* In OpencCV H: 0 - 180, S: 0 - 255, V: 0 - 255 */

                    Imgproc.cvtColor(smat, hmat, Imgproc.COLOR_BGR2HSV);
                    Imgproc.blur(hmat, hmat, new Size(3, 3));
                    msg = new Message();
                    msg.what = 1;
                    myHandle.sendMessage(msg);

                    rmat = new Mat(smat.rows(),smat.cols(),smat.type());
                    objCluster1 = new int[hmat.rows()][hmat.cols()];
                    isLabel = new boolean[hmat.rows()][hmat.cols()];


                    // main action, which make spread 8 operator possible
                    System.out.println(0);

                    boolean ch = false;
                    boolean ch1 = false;

                    for(int i = 0; i <  hmat.rows(); i++)
                        for(int j = 0; j < hmat.cols(); j++){
                            if(!isLabel[i][j]){
                                all.add(spread1(i, j, lab));

                                lab++;


                                if (i > (hmat.rows() / 3) && !ch) {
                                    ch = true;
                                    msg = new Message();
                                    msg.what = 1;
                                    myHandle.sendMessage(msg);
                                }
                                if (i > ((2 * hmat.rows()) / 3) && ch && !ch1) {
                                    ch1 = true;
                                    msg = new Message();
                                    msg.what = 1;
                                    myHandle.sendMessage(msg);
                                }
                            }
                        }
                    System.out.println(1);
                    msg = new Message();
                    msg.what = 1;
                    myHandle.sendMessage(msg);
                    //copy the nearby -1 point's rgb to the object
                    for(int i = 0; i <  hmat.rows(); i++)
                        for(int j = 0; j < hmat.cols(); j++){
                            if(objCluster1[i][j] == -1 )copyrandom(i,j, objCluster1);
                        }
                    msg = new Message();
                    msg.what = 1;
                    myHandle.sendMessage(msg);
                    System.out.println(4);

                    // make the -1 point to black

                    System.out.println(5);
                    // color the result mat
                    double temp1[][] = new double[lab][3];
                    for(int k = 0; k < lab; k++){
                        Random r = new Random();
                        temp1[k][0] = r.nextInt(256);
                        temp1[k][1] = r.nextInt(256);
                        temp1[k][2] = r.nextInt(256);
                    }
                    msg = new Message();
                    msg.what = 1;
                    myHandle.sendMessage(msg);



                    for(int i = 0; i <  hmat.rows(); i++)
                        for(int j = 0; j < hmat.cols(); j++){
                            if(objCluster1[i][j] != -1)rmat.put(i, j, temp1[objCluster1[i][j]]);
                            else rmat.put(i, j,new double[]{ new Random().nextInt(256), new Random().nextInt(256), new Random().nextInt(256)});
                        }

                    msg = new Message();
                    msg.what = 1;
                    myHandle.sendMessage(msg);

                    bmap1 = Bitmap.createBitmap(rmat.cols(), rmat.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(rmat, bmap1);


                    msg = new Message();
                    msg.what = 1;
                    myHandle.sendMessage(msg);

                    msg = new Message();
                    msg.what = 2;
                    myHandle.sendMessage(msg);

                    msg = new Message();
                    msg.what = 4;
                    myHandle.sendMessage(msg);



                    all(realpath);

                    for(int i = 0; i < all.size(); i++)
                        if(all.get(i).size() > th1){
                            objnum.add(all.get(i));
                            int temp = 0, tempx = 0, tempy = 0, tempd = 0;
                            double tx[] = new double[all.get(i).size()];
                            double ty[] = new double[all.get(i).size()];

                            while(temp < all.get(i).size()){
                                tempx += all.get(i).get(temp).get(0);
                                tempy += all.get(i).get(temp).get(1);
                                tempd += truedepth[all.get(i).get(temp).get(0)][all.get(i).get(temp).get(1)];
                                tx[temp] = all.get(i).get(temp).get(0);
                                ty[temp] = all.get(i).get(temp).get(1);


                                temp++;
                            }


                            if(temp != 0){
                                ArrayList<Integer> a = new ArrayList<Integer>();
                                a.add(tempx/temp);
                                a.add(tempy/temp);

                                ArrayList<Integer> b = new ArrayList<Integer>();
                                Double dtemp = getMin(tx);
                                b.add(dtemp.intValue());

                                dtemp = getMax(tx);
                                b.add(dtemp.intValue());

                                dtemp = getMin(ty);
                                b.add(dtemp.intValue());

                                dtemp = getMax(ty);
                                b.add(dtemp.intValue());

                                avpix.add(a);
                                avdep.add(tempd/(double)temp);
                                bound.add(b);
                            }
                            count++;
                        }



                    bmapaddred = bmap.copy(bmap.getConfig(), true);
                    Canvas canvas = new Canvas();
                    canvas.setBitmap(bmapaddred);

                    System.out.println(bmap.getHeight() + " " + bmap.getWidth());
                    msg = new Message();
                    msg.what = 3;
                    myHandle.sendMessage(msg);


                    msg = new Message();
                    msg.what = 6;
                    myHandle.sendMessage(msg);

                    iv.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {


                            int touchX = (int)(((double)event.getX()) * ((double)bmap.getWidth()/(double)v.getWidth()));
                            int touchY = (int)((double)event.getY() * ((double)bmap.getWidth()/(double)v.getWidth()));

                            //int touchX = (int)((double)event.getX());
                            //int touchY = (int)((double)event.getY());


                            System.out.println(touchX + " " + touchY);

                            int num = findMinDisPoint(touchX,touchY);
                            //System.out.println(avpix.get(num).get(1) + " " + avpix.get(num).get(0));
                            // double dis = Math.sqrt(Math.pow(avpix.get(num).get(0) - touchY, 2) + (Math.pow(avpix.get(num).get(1)  - touchX, 2)));
                            // System.out.println("distance : "+dis);
                            if(num != -1 ){


                                Paint mPaint = new Paint();
                                bmapaddred = bmap.copy(bmap.getConfig(), true);
                                Canvas canvas = new Canvas();
                                canvas.setBitmap(bmapaddred);
                                mPaint.setColor(Color.RED);
                                mPaint.setStyle(Paint.Style.STROKE);
                                mPaint.setStrokeWidth(3);
                                canvas.drawRect(bound.get(num).get(2), bound.get(num).get(0), bound.get(num).get(3), bound.get(num).get(1), mPaint);
                                canvas.drawBitmap(bmapaddred, 0, 0, mPaint);



                                Message msg = new Message();
                                msg.what = 3;
                                myHandle.sendMessage(msg);

                                msg = new Message();
                                msg.what = 7;

                                resd = avdep.get(num);
                                System.out.println(resd);

                                myHandle.sendMessage(msg);
                            };


                            return false;
                        }
                    });

                }
            }).start();
        }


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }





    void cluster(String name){
        Message msg = new Message();
        msg.what = 0;
        myHandle.sendMessage(msg);

        lab = 0;
        System.out.println("start0");
        Mat fsmat = Imgcodecs.imread(name);
        smat = new Mat();

        Imgproc.resize(fsmat, smat, new Size(fsmat.cols() / 10, fsmat.rows() / 10));
        System.out.println("start1");


        cmat = smat.clone();
        hmat = smat.clone();
        bmat = smat.clone();
        Imgproc.bilateralFilter(smat, bmat ,-1,30, 30);
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);
        //canny start

        Imgproc.cvtColor(smat, cmat, Imgproc.COLOR_BGR2GRAY);

        Imgproc.blur(cmat, cmat, new Size(3, 3));
        Imgproc.Canny(cmat, cmat, 80, 180);
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);
        //canny over

		 /* In OpencCV H: 0 - 180, S: 0 - 255, V: 0 - 255 */

        Imgproc.cvtColor(smat, hmat, Imgproc.COLOR_BGR2HSV);
        Imgproc.blur(hmat, hmat, new Size(3, 3));
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);

        rmat = new Mat(smat.rows(),smat.cols(),smat.type());
        objCluster1 = new int[hmat.rows()][hmat.cols()];
        isLabel = new boolean[hmat.rows()][hmat.cols()];


        // main action, which make spread 8 operator possible
        System.out.println(0);

        boolean ch = false;
        boolean ch1 = false;

        for(int i = 0; i <  hmat.rows(); i++)
            for(int j = 0; j < hmat.cols(); j++){
                if(!isLabel[i][j]){
                    all.add(spread1(i, j, lab));

                    lab++;


                    if (i > (hmat.rows() / 3) && !ch) {
                        ch = true;
                        msg = new Message();
                        msg.what = 1;
                        myHandle.sendMessage(msg);
                    }
                    if (i > ((2 * hmat.rows()) / 3) && ch && !ch1) {
                        ch1 = true;
                        msg = new Message();
                        msg.what = 1;
                        myHandle.sendMessage(msg);
                    }
                }
            }
        System.out.println(1);
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);
        //copy the nearby -1 point's rgb to the object
        for(int i = 0; i <  hmat.rows(); i++)
            for(int j = 0; j < hmat.cols(); j++){
                if(objCluster1[i][j] == -1 )copyrandom(i,j, objCluster1);
            }
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);
        System.out.println(4);

        // make the -1 point to black

        System.out.println(5);
        // color the result mat
        double temp1[][] = new double[lab][3];
        for(int k = 0; k < lab; k++){
            Random r = new Random();
            temp1[k][0] = r.nextInt(256);
            temp1[k][1] = r.nextInt(256);
            temp1[k][2] = r.nextInt(256);
        }
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);



        for(int i = 0; i <  hmat.rows(); i++)
            for(int j = 0; j < hmat.cols(); j++){
                if(objCluster1[i][j] != -1)rmat.put(i, j, temp1[objCluster1[i][j]]);
                else rmat.put(i, j,new double[]{ new Random().nextInt(256), new Random().nextInt(256), new Random().nextInt(256)});
            }

        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);

        bmap1 = Bitmap.createBitmap(rmat.cols(), rmat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(rmat, bmap1);


        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);

        msg = new Message();
        msg.what = 2;
        myHandle.sendMessage(msg);

        msg = new Message();
        msg.what = 4;
        myHandle.sendMessage(msg);

    }


    void all(String name) {
        Message msg = new Message();
        msg.what = 0;
        myHandle.sendMessage(msg);
        lab = 0;

        Mat fsmat = Imgcodecs.imread(name);
        smat = new Mat();
        Imgproc.resize(fsmat, smat, new Size(fsmat.cols() / 10, fsmat.rows() / 10));

        ;
        cmat = smat.clone();
        hmat = smat.clone();

        //canny start

        Imgproc.cvtColor(smat, cmat, Imgproc.COLOR_BGR2GRAY);

        Imgproc.blur(cmat, cmat, new Size(3, 3));
        Imgproc.Canny(cmat, cmat, 80, 180);

        //canny over

		 /* In OpencCV H: 0 - 180, S: 0 - 255, V: 0 - 255 */

        Imgproc.cvtColor(smat, hmat, Imgproc.COLOR_BGR2HSV);

        //rmat = new Mat(smat.rows(), smat.cols(), smat.type());
        objCluster = new int[hmat.rows()][hmat.cols()];
        isLabel = new boolean[hmat.rows()][hmat.cols()];
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);




        // main action, which make spread 8 operator possible
        System.out.println(10);

        boolean ch = false;
        boolean ch1 = false;
        for (int i = 0; i < hmat.rows(); i++)
            for (int j = 0; j < hmat.cols(); j++) {
                if (!isLabel[i][j]) {
                    spread(i, j, lab);

                    lab++;
                    if (i > (hmat.rows() / 3) && !ch) {
                        ch = true;
                        msg = new Message();
                        msg.what = 1;
                        myHandle.sendMessage(msg);
                    }
                    if (i > ((2 * hmat.rows()) / 3) && ch && !ch1) {
                        ch1 = true;
                        msg = new Message();
                        msg.what = 1;
                        myHandle.sendMessage(msg);
                    }
                }
            }
        System.out.println(40);
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);

        //copy the nearby -1 point's rgb to the -1 object
        for (int i = 0; i < hmat.rows(); i++)
            for (int j = 0; j < hmat.cols(); j++) {
                if (objCluster[i][j] == -1) copyrandom(i, j, objCluster);
            }
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);

        System.out.println(50);

        //record the ini depth
        double[] temp = new double[lab];
        for (int k = 0; k < lab; k++) temp[k] = -1;
        double[][] depth = new double[hmat.rows()][hmat.cols()];

        for (int i = hmat.rows() - 1; i >= 0; i--)
            for (int j = 0; j < hmat.cols(); j++) {

                if (objCluster[i][j] == -1)
                    depth[i][j] = (double) (hmat.rows() - 1 - i) / (double) (hmat.rows() - 1);
                else {
                    if (temp[objCluster[i][j]] == -1) {
                        temp[objCluster[i][j]] = (double) (hmat.rows() - 1 - i) / (double) (hmat.rows() - 1);

                        //System.out.println(temp);
                        depth[i][j] = temp[objCluster[i][j]];

                    } else depth[i][j] = temp[objCluster[i][j]];
                }
            }
        System.out.println(60);
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);
        double inidepth[] = new double[hmat.rows() * hmat.cols()];
        double dark[] = new double[hmat.rows() * hmat.cols()];
        double s[] = new double[hmat.rows() * hmat.cols()];

        for (int i = 0; i < hmat.rows() * hmat.cols(); i++) {

            //inidepth[i] = -267.75*Math.pow(e, 3)+319.16*Math.pow(e, 2)-53*Math.pow(e, 1)+20.326;
            inidepth[i] = (-223.2631) * Math.pow(depth[i / hmat.cols()][i % hmat.cols()], 3)
                    + 250.5925 * Math.pow(depth[i / hmat.cols()][i % hmat.cols()], 2)
                    - 26.4503 * Math.pow(depth[i / hmat.cols()][i % hmat.cols()], 1)
                    + 4.5235;

            dark[i] = getMin(smat.get(i / hmat.cols(), i % hmat.cols()));
            s[i] = getMax(smat.get(i / hmat.cols(), i % hmat.cols())) - getMin(smat.get(i / hmat.cols(), i % hmat.cols()));
        }
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);
        System.out.println(70);

        double dmin = getMin(dark);
        double dmax = getMax(dark);
        double smin = getMin(s);
        double smax = getMax(s);

        for (int i = 0; i < hmat.rows() * hmat.cols(); i++) {
            dark[i] = (dark[i] - dmin) / (dmax - dmin);
            s[i] = (s[i] - smin) / (smax - smin);

        }

        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);
        System.out.println(80);


        double realdepth[] = new double[hmat.rows() * hmat.cols()];
        truedepth = new double[hmat.rows()][hmat.cols()];


        for (int i = 0; i < hmat.rows() * hmat.cols(); i++) {
            /*
			 	realdepth[i] =
				(0.8984*inidepth[i]*dark[i])
			+   ((-9.2089)*dark[i]*Sfun(s[i],i/hmat.cols()))
			+	((0.5502)*inidepth[i]*mSfun(s[i],i/hmat.cols()));*/

            realdepth[i] =
                    (0.9745 * inidepth[i] * dark[i])
                            + ((-15.6272) * dark[i] * Sfun(s[i], i / hmat.cols()))
                            + ((0.6313) * inidepth[i] * mSfun(s[i], i / hmat.cols()))
                            +   5.3724;

            truedepth[i / hmat.cols()][i % hmat.cols()] = realdepth[i];
        }






        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);
        System.out.println(90);
        //double min = getMin(realdepth);
        //double max = getMax(realdepth);
        //double resultdepth[] = new double[hmat.rows() * hmat.cols()];

        resultmat = smat.clone();
        /*
        for (int i = 0; i < hmat.rows() * hmat.cols(); i++) {
            resultdepth[i] = ((realdepth[i] - min) / (max - min)) * 255;
            resultmat.put(i / hmat.cols(), i % hmat.cols(), new double[]{resultdepth[i], resultdepth[i], resultdepth[i]});

            //System.out.println(realdepth[i]);
        }
        */
        for (int i = 0; i < resultmat.rows(); i++)
            for (int j = 0; j < resultmat.cols(); j++){
                double temptry0 = resultmat.get(i,j)[0];
                double temptry1 = resultmat.get(i,j)[1];
                double temptry2 = resultmat.get(i,j)[2];
                resultmat.put(i,j,new double[] {temptry2,temptry1,temptry0});

            }

        bmap = Bitmap.createBitmap(resultmat.cols(), resultmat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(resultmat, bmap);
        msg = new Message();
        msg.what = 1;
        myHandle.sendMessage(msg);


        msg = new Message();
        msg.what = 2;
        myHandle.sendMessage(msg);




    }


    static void spread(int row, int col, int lab) {

        if (cmat.get(row, col)[0] == 255) {
            isLabel[row][col] = true;
            objCluster[row][col] = -1;

            return;
        }


        ArrayList<ArrayList<Integer>> Queue = new ArrayList<ArrayList<Integer>>();

        ArrayList<Integer> origin = new ArrayList<Integer>();

        origin.add(row);
        origin.add(col);
        Queue.add(origin);

        isLabel[row][col] = true;
        objCluster[row][col] = lab;

        while (Queue.size() > 0) {

            row = Queue.get(Queue.size() - 1).get(0);
            col = Queue.get(Queue.size() - 1).get(1);

            Queue.remove(Queue.size() - 1);


            if (hmat.rows() > (row - 1) && (row - 1) >= 0 && hmat.cols() > (col - 1) && (col - 1) >= 0
                    && !isOpGapOver(row, col, 0) && !isLabel[row - 1][col - 1]
                    && (cmat.get(row - 1, col)[0] != 255 || cmat.get(row, col - 1)[0] != 255)) {

                isLabel[row - 1][col - 1] = true;
                objCluster[row - 1][col - 1] = lab;

                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row - 1);
                data.add(col - 1);
                Queue.add(data);

            }

            if (hmat.rows() > (row - 1) && (row - 1) >= 0 && hmat.cols() > (col) && (col) >= 0
                    && !isOpGapOver(row, col, 1) && !isLabel[row - 1][col]) {

                isLabel[row - 1][col] = true;
                objCluster[row - 1][col] = lab;

                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row - 1);
                data.add(col);
                Queue.add(data);

            }

            if (hmat.rows() > (row - 1) && (row - 1) >= 0 && hmat.cols() > (col + 1) && (col + 1) >= 0
                    && !isOpGapOver(row, col, 2) && !isLabel[row - 1][col + 1]
                    && (cmat.get(row - 1, col)[0] != 255 || cmat.get(row, col + 1)[0] != 255)) {

                isLabel[row - 1][col + 1] = true;
                objCluster[row - 1][col + 1] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row - 1);
                data.add(col + 1);
                Queue.add(data);


            }

            if (hmat.rows() > (row) && (row) >= 0 && hmat.cols() > (col - 1) && (col - 1) >= 0
                    && !isOpGapOver(row, col, 3) && !isLabel[row][col - 1]) {

                isLabel[row][col - 1] = true;
                objCluster[row][col - 1] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row);
                data.add(col - 1);
                Queue.add(data);


            }

            if (hmat.rows() > (row) && (row) >= 0 && hmat.cols() > (col + 1) && (col + 1) >= 0
                    && !isOpGapOver(row, col, 4) && !isLabel[row][col + 1]) {

                isLabel[row][col + 1] = true;
                objCluster[row][col + 1] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row);
                data.add(col + 1);
                Queue.add(data);


            }

            if (hmat.rows() > (row + 1) && (row + 1) >= 0 && hmat.cols() > (col - 1) && (col - 1) >= 0
                    && !isOpGapOver(row, col, 5) && !isLabel[row + 1][col - 1]
                    && (cmat.get(row, col - 1)[0] != 255 || cmat.get(row + 1, col)[0] != 255)) {

                isLabel[row + 1][col - 1] = true;
                objCluster[row + 1][col - 1] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row + 1);
                data.add(col - 1);
                Queue.add(data);

            }

            if (hmat.rows() > (row + 1) && (row + 1) >= 0 && hmat.cols() > (col) && (col) >= 0
                    && !isOpGapOver(row, col, 6) && !isLabel[row + 1][col]) {

                isLabel[row + 1][col] = true;
                objCluster[row + 1][col] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row + 1);
                data.add(col);
                Queue.add(data);

            }

            if (hmat.rows() > (row + 1) && (row + 1) >= 0 && hmat.cols() > (col + 1) && (col + 1) >= 0
                    && !isOpGapOver(row, col, 7) && !isLabel[row + 1][col + 1]
                    && (cmat.get(row, col + 1)[0] != 255 || cmat.get(row + 1, col)[0] != 255)) {

                isLabel[row + 1][col + 1] = true;
                objCluster[row + 1][col + 1] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row + 1);
                data.add(col + 1);
                Queue.add(data);

            }
        }
        return;
    }


    static ArrayList<ArrayList<Integer>> spread1(int row, int col, int lab) {

        if (cmat.get(row, col)[0] == 255) {
            isLabel[row][col] = true;
            objCluster1[row][col] = -1;
            ArrayList<ArrayList<Integer>> pixs = new ArrayList<ArrayList<Integer>>();
            ArrayList<Integer> origin = new ArrayList<Integer>();
            origin.add(row);
            origin.add(col);
            pixs.add(origin);
            return pixs;
        }


        ArrayList<ArrayList<Integer>> Queue = new ArrayList<ArrayList<Integer>>();
        ArrayList<ArrayList<Integer>> pixs = new ArrayList<ArrayList<Integer>>();
        ArrayList<Integer> origin = new ArrayList<Integer>();

        origin.add(row);
        origin.add(col);
        Queue.add(origin);
        pixs.add(origin);
        isLabel[row][col] = true;
        objCluster1[row][col] = lab;

        while (Queue.size() > 0) {

            row = Queue.get(Queue.size() - 1).get(0);
            col = Queue.get(Queue.size() - 1).get(1);

            Queue.remove(Queue.size() - 1);


            if (hmat.rows() > (row - 1) && (row - 1) >= 0 && hmat.cols() > (col - 1) && (col - 1) >= 0
                    && !isOpGapOver(row, col, 0) && !isLabel[row - 1][col - 1]
                    && (cmat.get(row - 1, col)[0] != 255 || cmat.get(row, col - 1)[0] != 255)) {

                isLabel[row - 1][col - 1] = true;
                objCluster1[row - 1][col - 1] = lab;

                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row - 1);
                data.add(col - 1);
                Queue.add(data);
                pixs.add(data);
            }

            if (hmat.rows() > (row - 1) && (row - 1) >= 0 && hmat.cols() > (col) && (col) >= 0
                    && !isOpGapOver(row, col, 1) && !isLabel[row - 1][col]) {

                isLabel[row - 1][col] = true;
                objCluster1[row - 1][col] = lab;

                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row - 1);
                data.add(col);
                Queue.add(data);
                pixs.add(data);
            }

            if (hmat.rows() > (row - 1) && (row - 1) >= 0 && hmat.cols() > (col + 1) && (col + 1) >= 0
                    && !isOpGapOver(row, col, 2) && !isLabel[row - 1][col + 1]
                    && (cmat.get(row - 1, col)[0] != 255 || cmat.get(row, col + 1)[0] != 255)) {

                isLabel[row - 1][col + 1] = true;
                objCluster1[row - 1][col + 1] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row - 1);
                data.add(col + 1);
                Queue.add(data);
                pixs.add(data);

            }

            if (hmat.rows() > (row) && (row) >= 0 && hmat.cols() > (col - 1) && (col - 1) >= 0
                    && !isOpGapOver(row, col, 3) && !isLabel[row][col - 1]) {

                isLabel[row][col - 1] = true;
                objCluster1[row][col - 1] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row);
                data.add(col - 1);
                Queue.add(data);
                pixs.add(data);

            }

            if (hmat.rows() > (row) && (row) >= 0 && hmat.cols() > (col + 1) && (col + 1) >= 0
                    && !isOpGapOver(row, col, 4) && !isLabel[row][col + 1]) {

                isLabel[row][col + 1] = true;
                objCluster1[row][col + 1] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row);
                data.add(col + 1);
                Queue.add(data);
                pixs.add(data);

            }

            if (hmat.rows() > (row + 1) && (row + 1) >= 0 && hmat.cols() > (col - 1) && (col - 1) >= 0
                    && !isOpGapOver(row, col, 5) && !isLabel[row + 1][col - 1]
                    && (cmat.get(row, col - 1)[0] != 255 || cmat.get(row + 1, col)[0] != 255)) {

                isLabel[row + 1][col - 1] = true;
                objCluster1[row + 1][col - 1] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row + 1);
                data.add(col - 1);
                Queue.add(data);
                pixs.add(data);
            }

            if (hmat.rows() > (row + 1) && (row + 1) >= 0 && hmat.cols() > (col) && (col) >= 0
                    && !isOpGapOver(row, col, 6) && !isLabel[row + 1][col]) {

                isLabel[row + 1][col] = true;
                objCluster1[row + 1][col] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row + 1);
                data.add(col);
                Queue.add(data);
                pixs.add(data);
            }

            if (hmat.rows() > (row + 1) && (row + 1) >= 0 && hmat.cols() > (col + 1) && (col + 1) >= 0
                    && !isOpGapOver(row, col, 7) && !isLabel[row + 1][col + 1]
                    && (cmat.get(row, col + 1)[0] != 255 || cmat.get(row + 1, col)[0] != 255)) {

                isLabel[row + 1][col + 1] = true;
                objCluster1[row + 1][col + 1] = lab;
                ArrayList<Integer> data = new ArrayList<Integer>();
                data.add(row + 1);
                data.add(col + 1);
                Queue.add(data);
                pixs.add(data);
            }
        }
        return pixs;
    }










    static boolean isOpGapOver(int row, int col, int patition) {
        double gap[] = new double[3];
        for (int i = 0; i < 3; i++) {
            switch (patition) {
                case 0:
                    gap[i] = Math.abs(hmat.get(row - 1, col - 1)[i] - hmat.get(row, col)[i]);
                    break;
                case 1:
                    gap[i] = Math.abs(hmat.get(row - 1, col)[i] - hmat.get(row, col)[i]);
                    break;
                case 2:
                    gap[i] = Math.abs(hmat.get(row - 1, col + 1)[i] - hmat.get(row, col)[i]);
                    break;
                case 3:
                    gap[i] = Math.abs(hmat.get(row, col - 1)[i] - hmat.get(row, col)[i]);
                    break;
                case 4:
                    gap[i] = Math.abs(hmat.get(row, col + 1)[i] - hmat.get(row, col)[i]);
                    break;
                case 5:
                    gap[i] = Math.abs(hmat.get(row + 1, col - 1)[i] - hmat.get(row, col)[i]);
                    break;
                case 6:
                    gap[i] = Math.abs(hmat.get(row + 1, col)[i] - hmat.get(row, col)[i]);
                    break;
                case 7:
                    gap[i] = Math.abs(hmat.get(row + 1, col + 1)[i] - hmat.get(row, col)[i]);
                    break;
            }
        }

        if (isOverTh(gap)) return true;
        else return false;
    }

    static boolean isOverTh(double[] gap) {
        if ((gap[0] + gap[1] + gap[2]) > (th[0] + th[1] + th[2])) return true;

        return false;
    }


    static void copyrandom(int i, int j, int[][] o) {
        int t = 0;
        while (o[i][j] == -1 && t < 100) {
            switch (new Random().nextInt(8)) {
                case 0:
                    if (i - 1 >= 0 && j - 1 >= 0)
                        o[i][j] = o[i - 1][j - 1];
                    break;
                case 1:
                    if (i - 1 >= 0)
                        o[i][j] = o[i - 1][j];
                    break;
                case 2:
                    if (i - 1 >= 0 && j + 1 < hmat.cols())
                        o[i][j] = o[i - 1][j + 1];
                    break;
                case 3:
                    if (j - 1 >= 0)
                        o[i][j] = o[i][j - 1];
                    break;
                case 4:
                    if (j + 1 < hmat.cols())
                        o[i][j] = o[i][j + 1];
                    break;
                case 5:
                    if (i + 1 < hmat.rows() && j - 1 >= 0)
                        o[i][j] = o[i + 1][j - 1];
                    break;
                case 6:
                    if (i + 1 < hmat.rows())
                        o[i][j] = o[i + 1][j];
                    break;
                case 7:
                    if (i + 1 < hmat.rows() && j + 1 < hmat.cols())
                        o[i][j] = o[i + 1][j + 1];
                    break;
            }
            t++;
        }


    }


    static double Sfun(double a, int b) {
        double temp = (1 / (1 + Math.pow(Math.E, (-1 * (smat.rows() - 1 - b)))));
        double answer = Math.pow(a, 1 - temp);
        //System.out.println(answer);
        return answer;

    }

    static double mSfun(double a, int b) {
        double temp = (1 / (1 + Math.pow(Math.E, (-1 * (smat.rows() - 1 - b)))));
        double answer = Math.pow(1 - a, 1 - temp);
        //System.out.println(answer);
        return answer;

    }

    public static double getMax(double[] inputArray) {
        double maxValue = inputArray[0];
        for (int i = 1; i < inputArray.length; i++) {
            if (inputArray[i] > maxValue) {
                maxValue = inputArray[i];
            }
        }
        return maxValue;
    }

    public static double getMin(double[] inputArray) {
        double minValue = inputArray[0];
        for (int i = 1; i < inputArray.length; i++) {
            if (inputArray[i] < minValue) {
                minValue = inputArray[i];
            }
        }
        return minValue;
    }


    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.example.root.depth/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app deep link URI is correct.
                Uri.parse("android-app://com.example.root.depth/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }
}
