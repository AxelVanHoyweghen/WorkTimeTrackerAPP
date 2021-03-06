package com.worktimetrackerapp.gui_controllers;


import android.annotation.SuppressLint;
import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.QueryRow;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.CalendarMode;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener;
import com.worktimetrackerapp.DB;
import com.worktimetrackerapp.R;
import com.worktimetrackerapp.util.AgendaArrayAdapter;
import com.worktimetrackerapp.util.OnObjectChangeListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


//calendar credit to https://github.com/prolificinteractive/material-calendarview/blob/master/docs/DECORATORS.md
//  and  https://www.youtube.com/watch?v=RN4Zmxlah_I

public class Agenda_Controller extends Fragment {
    View currentView;
    private ListView agendalist;
    private AgendaArrayAdapter aaa;
    LiveQuery liveQuery;
    DB app;
    TextView agendaheader;
    String selectedday;

    com.couchbase.lite.View viewItemsByDate;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        currentView = inflater.inflate(R.layout.agenda, container, false);
        agendalist = (ListView) currentView.findViewById(R.id.agenda_list_view);
        TextView emptyText = (TextView) currentView.findViewById(R.id.empty_agendalist);
        agendalist.setEmptyView(emptyText);
        app = (DB) getActivity().getApplication();
        agendaheader = new TextView(getContext());
        agendaheader.setTextSize(getResources().getDimension(R.dimen.listview_header));


        //Agenda items
        final MaterialCalendarView materialCalendarView = (MaterialCalendarView) currentView.findViewById(R.id.calendarView);


                Calendar calendar = Calendar.getInstance();
                materialCalendarView.setSelectedDate(calendar.getTime());

                Calendar instance1 = Calendar.getInstance();
                instance1.set(instance1.get(Calendar.YEAR), Calendar.JANUARY, 1);

                Calendar instance2 = Calendar.getInstance();
                instance2.set(instance2.get(Calendar.YEAR) + 2, Calendar.OCTOBER, 31);

                materialCalendarView.state().edit()
                        .setFirstDayOfWeek(Calendar.SUNDAY)
                        .setMinimumDate(CalendarDay.from(1900, 1, 1))
                        .setMaximumDate(CalendarDay.from(2100, 12, 31))
                        .setCalendarDisplayMode(CalendarMode.MONTHS)
                        .commit();

        try {
            SimpleDateFormat today = new SimpleDateFormat("yyyy-MM-dd");
            selectedday = today.format(calendar.getTime());
            agendaheader.setText(selectedday);
            agendalist.addHeaderView(agendaheader);
            agendaheader.setFocusable(false);
            startShowList();
            startLiveQuery(selectedday);
        } catch (Exception e) {
            app.showErrorMessage("Error initializing CBLite", e);
        }

       materialCalendarView.setOnDateChangedListener(new OnDateSelectedListener() {
            @Override
            public void onDateSelected(@NonNull MaterialCalendarView widget, @NonNull CalendarDay date, boolean selected) {
                // Toast.makeText(getActivity(), "" + date, Toast.LENGTH_SHORT).show();
                try {
                    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
                    selectedday = dateFormatter.format(date.getDate());
                    agendalist.removeHeaderView(agendaheader);
                    agendaheader.setText(selectedday);
                    agendalist.addHeaderView(agendaheader);
                    liveQuery.stop();
                    startLiveQuery(selectedday);
                } catch (Exception e) {
                    DB app = (DB) getContext();
                    app.showErrorMessage("Error initializing CBLite", e);
                }

            }
        });

        //floating button
            FloatingActionButton fab = currentView.findViewById(R.id.agendafab);
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        PopUpWindows ipp = new PopUpWindows();
                        ipp.showInfoPopup(null, getActivity(),false, null);
                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            });

            //change listener for job
        app.getcurrentJob().setOnObjectChangeListener(new OnObjectChangeListener() {
            @Override
            public void OnObjectChanged(Object newobj) {
                if(currentView != null) {
                    try {
                        agendalist.removeHeaderView(agendaheader);
                        agendaheader.setText(selectedday);
                        agendalist.addHeaderView(agendaheader);
                        liveQuery.stop();
                        startLiveQuery(selectedday);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        return currentView;
    }

    protected void startShowList() throws Exception {
        DB app = (DB) getActivity().getApplication();
        Database mydb = app.getMydb();
        viewItemsByDate = mydb.getView("TaskScheduledStartDate");
        viewItemsByDate.setMap(new Mapper(){
            @Override
            public void map(Map<String, Object> document, Emitter emitter){
                if(document.get("type").equals("Task")) {
                    if(document.get("TaskScheduledStartDate") != null) {
                            String date = (String) document.get("TaskScheduledStartDate");
                            emitter.emit(date, null);
                    }
                }//end if
            }
        },"4");

        initItemListAdapter();
    }

    private void initItemListAdapter() {
        DB app = (DB) getActivity().getApplicationContext();
        aaa = new AgendaArrayAdapter(
                app,
                R.layout.agenda_row_layout,
                R.id.agenda_row_task_name,
                R.id.agenda_row_task_info,
                new ArrayList<QueryRow>()
        );
        agendalist.setAdapter(aaa);
        agendalist.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                if(position != 0) {
                    QueryRow row = (QueryRow) adapterView.getItemAtPosition(position);
                    Document document = row.getDocument();
                    try {
                        PopUpWindows ipp = new PopUpWindows();
                        ipp.showInfoPopup(document, getActivity(), false, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }) ;
    }

    private void startLiveQuery(String SelectedDay){
        final String jobname = app.getcurrentJob().getcurrentJob().toString();

        final DB app = (DB) getActivity().getApplication();
            liveQuery = null;
            Query MyQuery = viewItemsByDate.createQuery();
            MyQuery.setDescending(true);
            MyQuery.setStartKey(SelectedDay);
            MyQuery.setEndKey(SelectedDay);
            liveQuery = MyQuery.toLiveQuery();
            liveQuery.addChangeListener(new LiveQuery.ChangeListener() {
                public void changed(final LiveQuery.ChangeEvent event) {
                    app.runOnUiThread(new Runnable() {
                        public void run() {
                            aaa.clear();
                            for (Iterator<QueryRow> it = event.getRows(); it.hasNext();) {
                                QueryRow item = it.next();
                                System.out.println("jobtitle"+ app.getMydb().getDocument(item.getDocumentId()).getProperty("jobtitle"));
                                System.out.println("jobname: " + jobname);
                                if(app.getMydb().getDocument(item.getDocumentId()).getProperty("jobtitle").equals(jobname)) {
                                    aaa.add(item);
                                }
                            }
                            aaa.notifyDataSetChanged();
                        }
                    });
                }
            });
            liveQuery.start();
    }


}



