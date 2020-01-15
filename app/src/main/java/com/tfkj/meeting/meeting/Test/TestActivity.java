package com.tfkj.meeting.meeting.Test;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.tfkj.meeting.meeting.R;
import com.tfkj.meeting.meeting.service.CService;
import com.tfkj.meeting.meeting.service.SService;

public class TestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        String message = "TEST";
        CService.getInstance().writeToAllService(message.getBytes());
        SService.getInstance().writeToAllClient(message.getBytes());
    }
}
