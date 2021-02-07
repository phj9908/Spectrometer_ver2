package org.techtown.capture.myapplication1224_1;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

import static org.techtown.capture.myapplication1224_1.MainActivity.count_frame;
import static org.techtown.capture.myapplication1224_1.MainActivity.sum_alpha;
import static org.techtown.capture.myapplication1224_1.MainActivity.sum_beta;
import static org.techtown.capture.myapplication1224_1.MainActivity.sum_p0;

public class DataActivity extends AppCompatActivity {

    String[] text2_array= new String[4];
    double[] data_aver_array= new double[8];


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);
        Button button = findViewById(R.id.button);
        Button button3 = findViewById(R.id.button3);
        Button reset_button = findViewById(R.id.reset_button);
        TextView count_tx = (TextView)findViewById(R.id.count_textView);
        TextView tx5 = (TextView)findViewById(R.id.textView5);
        TextView alpha_tx = (TextView)findViewById(R.id.alpha_textView);
        TextView beta_tx = (TextView)findViewById(R.id.beta_textView);
        TextView p0_tx = (TextView)findViewById(R.id.p0_textView);

        Intent intent = getIntent();
        double data_src[] = intent.getExtras().getDoubleArray("data"); // alpha,beta,p0,count_frame 배열

        double count_frame_data=0;
        count_frame_data=data_src[3];

        for(int i=0; i<8;i++) {

            if(i<4) {
                data_aver_array[i] = data_src[i] / count_frame_data; //  누적 값(alpha,beta,p0)/ 프레임 갯수
                text2_array[i]=""+data_aver_array[i];
                if(i==3)text2_array[i]="        #frame: " + (int)count_frame_data;
            }else if(i>3) data_aver_array[i] = data_src[i]; //roi_rect

        }

        count_tx.setText(text2_array[3]); //count_frame
        tx5.setText("        # averaging data = cumulative data / frame ");
        alpha_tx.setText("  averaging alpha : " + text2_array[0]); // alpha
        beta_tx.setText("  averaging beta : " + text2_array[1]); // beta
        p0_tx.setText("  averaging p0 : " +text2_array[2]); // p0

        setResult(Activity.RESULT_OK, intent); //이 액티비티가 종료되기전에 정상적으로 수행되었다고 메인액티비티에 응답

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); //다시 MAtinActivity로 돌아감
            }
        });

        //Intent launch_intent = new Intent(DataActivity.this,LaunchActivity.class);

//        button2.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                finish();
//                //startActivity(launch_intent); // 시작화면으로 돌아감 혹은 바로 다른모드 액티비티로 넘어가던가
//            }
//        });

//        final Bundle result = result_intent.getExtras();

        Intent sensing_intent = new Intent(DataActivity.this,SensingActivity.class);

        button3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                sensing_intent.putExtra("sensing_data",data_aver_array);
                startActivity(sensing_intent); finish();

            }
        });

        reset_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                    sum_alpha=0;
                    sum_beta=0;
                    sum_p0=0;
                    count_frame = 0;

                alpha_tx.setText(" ... "); // alpha
                beta_tx.setText(" ... "); // beta
                p0_tx.setText(" ... "); // p0

            }
        });


    }
}

