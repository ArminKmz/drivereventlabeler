package com.sharif.armin.drivingeventlabeler.activity;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.sharif.armin.drivingeventlabeler.R;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class GuidedLabeling extends AppCompatActivity implements PropertyChangeListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guided_labeling);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        Object source = propertyChangeEvent.getSource();
        String name = propertyChangeEvent.getPropertyName();
        Object oldvalue = propertyChangeEvent.getOldValue();
        Object newvalue = propertyChangeEvent.getNewValue();
        //TODO what ever is necessary!!!!
    }
}
