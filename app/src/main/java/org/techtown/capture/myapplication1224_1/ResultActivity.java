package org.techtown.capture.myapplication1224_1;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import static org.techtown.capture.myapplication1224_1.SensingActivity.sum_B;
import static org.techtown.capture.myapplication1224_1.SensingActivity.sum_G;
import static org.techtown.capture.myapplication1224_1.SensingActivity.sum_R;

public class ResultActivity extends AppCompatActivity {

    String[] text_array= new String[3];
    double[] pixel_aver_array= new double[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        Button back_button = findViewById(R.id.back_b);
        Button reset_button = findViewById(R.id.reset_b);
        TextView R_tx = (TextView)findViewById(R.id.R_textView);
        TextView G_tx = (TextView)findViewById(R.id.G_textView);
        TextView B_tx = (TextView)findViewById(R.id.B_textView);
        //TextView result_tx = (TextView)findViewById(R.id.result_tx);
        TextView sensing_tx = (TextView)findViewById(R.id.sensing_tx);

        Intent intent = getIntent();
        double data_src[] = intent.getExtras().getDoubleArray("result"); // R,G,B result[3]

        for(int i=0; i<3;i++) {

                pixel_aver_array[i] = data_src[i];

            switch(i){
                case 0: text_array[0] = " B: " + pixel_aver_array[i]; break;
                case 1: text_array[1]= " G: " +pixel_aver_array[i]; break;
                case 2: text_array[2]= " R: " + pixel_aver_array[i]; break; }

        }

        B_tx.setText(text_array[0]); G_tx.setText(text_array[1]); R_tx.setText(text_array[2]);
        sensing_tx.setText(" "+0.5+"ppm");

        back_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        reset_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                sum_B=0;
                sum_G=0;
                sum_R=0;

                R_tx.setText(" ... ");  B_tx.setText(" ... "); G_tx.setText(" ... "); sensing_tx.setText(" ... ");
            }
        });



    }
}