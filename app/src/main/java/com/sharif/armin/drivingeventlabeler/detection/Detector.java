package com.sharif.armin.drivingeventlabeler.detection;

import com.sharif.armin.drivingeventlabeler.sensor.SensorSample;
import com.sharif.armin.drivingeventlabeler.sensor.Sensors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

import mr.go.sgfilter.SGFilter;

public class Detector {

    private ArrayList<DetectorObserver> mObservers;
    public void registerObserver(DetectorObserver detectorObserver){
        if(!mObservers.contains(detectorObserver)) {
            mObservers.add(detectorObserver);
        }
    }
    public void removeObserver(DetectorObserver detectorObserver){
        if(mObservers.contains(detectorObserver)) {
            mObservers.remove(detectorObserver);
        }
    }
    public void notifyObserversEventDetected(Event event){
        for (DetectorObserver observer: mObservers) {
            observer.onEventDetected(event);
        }
    }

    private Thread threadSensor = null;

    private boolean TestFlag = false;

    public static int X = 0, Y = 1, Z = 2;

    public static int windowSize = 40;
    public static int overLap = 30;
    public static int stepSize = windowSize - overLap;
    public static int step = stepSize - 1;

    private int savgolNl = 15;
    private int savgolNr = 15;
    private int savgolDegree = 1;
    private double[] savgolcoeffs;
    private SGFilter sgFilter;
    private int savgolWindowSize = 1 + savgolNl + savgolNr;

    private int sensorFreq;
    private Sensors sensors;
    private SensorTest sensorsTest;

    private LinkedList<float[]> lacFilteredWindow;
    private LinkedList<float[]> gyrFilteredWindow;
    private LinkedList<Long> time;

    private LinkedList<float[]> lacSavgolWindow;
    private LinkedList<float[]> gyrSavgolWindow;

    private float[] lacEnergy = new float[2];
    private float[] lacMean = new float[2];
    private float[] gyrEnergy = new float[1];
    private float[] gyrMean = new float[1];

    public LinkedList<Event> eventList;

    public Detector(int sensorFreq, Sensors sensors){
        mObservers = new ArrayList<>();
        this.sensorFreq = sensorFreq;

        eventList = new LinkedList<>();

        lacFilteredWindow = new LinkedList<>();
        gyrFilteredWindow = new LinkedList<>();
        time = new LinkedList<>();
        lacSavgolWindow = new LinkedList<>();
        gyrSavgolWindow = new LinkedList<>();
        this.sensors = sensors;

        sgFilter = new SGFilter(savgolNl, savgolNr);
        savgolcoeffs = SGFilter.computeSGCoefficients(savgolNl, savgolNr, savgolDegree);
    }

    public Detector(int sensorFreq, SensorTest sensorTest) {
        mObservers = new ArrayList<>();
        this.sensorFreq = sensorFreq;

        TestFlag = true;

        eventList = new LinkedList<>();

        lacFilteredWindow = new LinkedList<float[]>();
        gyrFilteredWindow = new LinkedList<float[]>();
        time = new LinkedList<>();
        lacSavgolWindow = new LinkedList<float[]>();
        gyrSavgolWindow = new LinkedList<float[]>();
        this.sensorsTest = sensorTest;

        sgFilter = new SGFilter(savgolNl, savgolNr);
        savgolcoeffs = SGFilter.computeSGCoefficients(savgolNl, savgolNr, savgolDegree);
    }

    public void stop() {
        threadSensor.interrupt();
    }

    public void start() {
        if(threadSensor != null){
            threadSensor.interrupt();
        }
        threadSensor = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    try{
                        synchronized (this) {
                            if (TestFlag && sensorsTest.fin) {
                                notifyObserversEventDetected(new Event(0,0,"Finish"));
                                break;
                            }
                            if (TestFlag && !sensorsTest.start)
                                continue;
                            if (SensorAdd())
                                step += 1;
                            if (step == stepSize) {
                                step = 0;
                                EventDetector(lacFilteredWindow, lacEnergy, lacMean, gyrFilteredWindow, gyrEnergy, gyrMean, time);
                            }
                        }
                        Thread.sleep(1000 / Detector.this.sensorFreq);
                    }catch (InterruptedException e){
                        System.err.println("threadSensor");
                        e.printStackTrace();
                    }
                }
            }
        });
        threadSensor.start();
    }

    private void EventDetector(LinkedList<float[]> lac, float[] lacEnergy, float[] lacMean, LinkedList<float[]> gyr, float[] gyrEnergy, float[] gyrMean, LinkedList<Long> time) {
        Event event1, event2, event3;
        LinkedList <Float> lacX = new LinkedList<>(),
                lacY = new LinkedList<>();

        for (float[] t: lac) {
            lacX.add(t[0]);
            lacY.add(t[1]);
        }

        event1 = BrakeEventDetector.brakeDetect(lacY, lacEnergy[Y], lacMean[Y], time);
        event2 = TurnEventDetector.turnDetect(gyrEnergy[0], gyrMean[0], time);
        event3 = LaneChangeDetector.lanechangeDetect(lacX, gyrEnergy[0], lacEnergy[X], time);
        if (event1 != null){
            eventList.add(event1);
            notifyObserversEventDetected(event1);
        }else if (event2 != null){
            eventList.add(event2);
            notifyObserversEventDetected(event2);
        }else if (event3 != null){
            eventList.add(event3);
            notifyObserversEventDetected(event3);
        }
    }

    private boolean SensorAdd() {
        SensorSample accSS, gyrSS;
        if (TestFlag) {
            synchronized (sensorsTest) {
                accSS =  sensorsTest.getAcc();
                gyrSS =  sensorsTest.getGyr();
            }
        }
        else {
            accSS =  sensors.getLinearAccelerationPhone();
            gyrSS =  sensors.getAngularVelocityPhone();
        }
        float[] acc = accSS.values,
                gyr = gyrSS.values;
        long accTime = accSS.time;
        if (lacFilteredWindow.size() < windowSize) {
            lacSavgolWindow.add(new float[]{acc[0], acc[1]});
            gyrSavgolWindow.add(new float[]{gyr[2]});
            if (lacSavgolWindow.size() > savgolNr + savgolNl + 1) {
                lacSavgolWindow.removeFirst();
                gyrSavgolWindow.removeFirst();
            }
            if (lacSavgolWindow.size() == savgolNl + savgolNr + 1){
                SensorSmooth(accTime);
            }
            return false;
        }
        else if (lacFilteredWindow.size() == windowSize && gyrFilteredWindow.size() == windowSize){
            lacSavgolWindow.add(new float[]{acc[0], acc[1]});
            gyrSavgolWindow.add(new float[]{gyr[2]});

            lacSavgolWindow.removeFirst();
            gyrSavgolWindow.removeFirst();

            SensorSmooth(accTime);
            return true;
        }
        return false;
    }

    private void SensorSmooth(long time) {
        this.time.add(time - savgolNl * 1000 / sensorFreq);
        if (this.time.size() > windowSize) {
            this.time.removeFirst();
        }
        float[] lacX = new float[savgolWindowSize],
                lacY = new float[savgolWindowSize],
                gyrZ = new float[savgolWindowSize];
        Iterator<float[]> laciterator = lacSavgolWindow.iterator();
        Iterator<float[]> gyriterator = gyrSavgolWindow.iterator();

        for (int i = 0; i < savgolWindowSize; i++) {
            float[] t = laciterator.next();
            lacX[i] = t[X];
            lacY[i] = t[Y];
            t = gyriterator.next();
            gyrZ[i] = t[0];
        }
        lacFilteredWindow.add(new float[]{
                sgFilter.smooth(lacX, savgolNl, savgolNl + 1, savgolcoeffs)[0],
                sgFilter.smooth(lacY, savgolNl, savgolNl + 1, savgolcoeffs)[0]
        });
        gyrFilteredWindow.add(new float[]{
                sgFilter.smooth(gyrZ, savgolNl, savgolNl + 1, savgolcoeffs)[0]
        });

        lacEnergy[X] += (float) Math.pow(lacFilteredWindow.getLast()[X], 2);
        lacEnergy[Y] += (float) Math.pow(lacFilteredWindow.getLast()[Y], 2);

        lacMean[X] += lacFilteredWindow.getLast()[X] / windowSize;
        lacMean[Y] += lacFilteredWindow.getLast()[Y] / windowSize;

        gyrEnergy[0] += (float) Math.pow(gyrFilteredWindow.getLast()[0], 2);

        gyrMean[0] += gyrFilteredWindow.getLast()[0] / windowSize;

        if (lacFilteredWindow.size() > windowSize){
            lacEnergy[X] -= (float) Math.pow(lacFilteredWindow.getFirst()[X], 2);
            lacEnergy[Y] -= (float) Math.pow(lacFilteredWindow.getFirst()[Y], 2);

            lacMean[X] -= lacFilteredWindow.getFirst()[X] / windowSize;
            lacMean[Y] -= lacFilteredWindow.getFirst()[Y] / windowSize;

            lacFilteredWindow.removeFirst();
        }
        if (gyrFilteredWindow.size() > windowSize){
            gyrEnergy[0] -= (float) Math.pow(gyrFilteredWindow.getFirst()[0], 2);
            gyrMean[0] -= gyrFilteredWindow.getFirst()[0] / windowSize;

            gyrFilteredWindow.removeFirst();
        }
    }
}

class BrakeEventDetector {
    private static int MinDuration = 1 * 1000;
    private static int MaxDuration = 10 * 1000;
    private static float lacYEnergyThreshold = 0.1f;
    private static float VarThreshold = 0.02f;
    private static float AcceptFunctionThreshold = -0.1f;
    private static boolean brakeEvent = false;
    private static long brakeStart, brakeStop;
    private static LinkedList<Float> brakeWindow = new LinkedList<>();

    public static boolean AcceptWindowFunction(float Mean){
        return Mean < AcceptFunctionThreshold;
    }

    public static boolean AcceptEventFunction(LinkedList<Float> event){
        double sqSum = 0, sum = 0;
        Iterator<Float> iter = event.iterator();

        while (iter.hasNext()) {
            double t = iter.next();
            sum += t;
            sqSum += t * t;
        }
        int n = event.size();
        double mean = sum / n;
        double var = sqSum / n - mean * mean;
        return var > VarThreshold;
    }

    public static Event brakeDetect(LinkedList<Float> lac, float lacYEnergy, float lacYMean, LinkedList<Long> time) {
        if (lacYEnergy / Detector.windowSize >= BrakeEventDetector.lacYEnergyThreshold &&
                BrakeEventDetector.AcceptWindowFunction(lacYMean)) {
            if (!brakeEvent) {
                brakeWindow = (LinkedList<Float>) lac.clone();
                brakeStart = time.getFirst();
            }
            else {
                brakeWindow.addAll(lac.subList(Detector.overLap, Detector.windowSize));
            }
            brakeEvent = true;
        }
        else if (brakeEvent) {
            brakeEvent = false;
            brakeStop = time.get(Detector.overLap);
            if (BrakeEventDetector.AcceptEventFunction(brakeWindow) &&
                    brakeStop - brakeStart < BrakeEventDetector.MaxDuration &&
                    brakeStop - brakeStart > BrakeEventDetector.MinDuration ){
                return new Event(brakeStart, brakeStop, "brake");
            }
        }
        return null;
    }

}

class TurnEventDetector {
    private static int MinDuration = 2 * 1000;
    private static int MaxDuration = 10 * 1000;
    private static float gyrZEnergyThreshold = 0.01f;
    private static float AcceptFunctionThreshold = 0.1f;
    private static long turnStart, turnStop;
    private static boolean turnEvent = false;


    private static boolean AcceptWindowFunction(float Mean){
        return Math.abs(Mean) >= AcceptFunctionThreshold;
    }

    public static Event turnDetect(float gyrZEnergy, float gyrZMean, LinkedList<Long> time) {
        if (gyrZEnergy / Detector.windowSize >= TurnEventDetector.gyrZEnergyThreshold && TurnEventDetector.AcceptWindowFunction(gyrZMean)) {
            if (!turnEvent) {
                turnStart = time.getFirst();
            }
            turnEvent = true;
        }
        else if (turnEvent) {
            turnStop = time.get(Detector.overLap);
            turnEvent = false;
            if (turnStop - turnStart < TurnEventDetector.MaxDuration && turnStop - turnStart > TurnEventDetector.MinDuration){
                return new Event(turnStart, turnStop, "turn");
            }
        }
        return null;
    }
}

class LaneChangeDetector {
    private static int MinDuration = 2 * 1000;
    public static int MaxDuration = 10 * 1000;
    private static float lacEnergyThreshold = 0.04f;
    private static float gyrEnergyThreshold = 0.001f;
    private static int subSampleParameter = 10;
    private static boolean laneChangeEvent = false;
    private static long laneChangeStart, laneChangeStop;
    private static LinkedList<Float> laneChangeWindow = new LinkedList<>();
    private static float dtwThreshold = 0.2f;

    private static boolean dtwDetection(LinkedList<Float> laneChangeWindow) {
        ArrayList<Float> subSample = new ArrayList<>();
        int i = 10;
        for (Float sample: laneChangeWindow) {
            if (i == subSampleParameter){
                i = 0;
                subSample.add(sample);
            }
            i += 1;
        }
        float[] template1 = new float[25],
                template2 = new float[25];
        for (int j = 0; j < 25; j++) {
            template1[j] = (float) Math.sin(((float)j/25.0f) * 2 * Math.PI);
            template2[j] = -template1[j];
        }

        float[] win = new float[subSample.size()];
        Iterator<Float> subitr = subSample.listIterator(0);
        for (int k = 0; k < subSample.size(); k++) {
            win[k] = subitr.next();
        }

        DTW dtw = new DTW(win, template1);
        DTW dtw1 = new DTW(win, template2);
        float dis = Math.min(dtw.getDistance(), dtw1.getDistance());
        if (dis < dtwThreshold){
            return true;
        }
        return false;
    }

    public static Event lanechangeDetect(LinkedList<Float> lac, float gyrZEnergy, float lacXEnergy, LinkedList<Long> time) {
        if (gyrZEnergy / Detector.windowSize >= LaneChangeDetector.gyrEnergyThreshold || lacXEnergy / Detector.windowSize >= LaneChangeDetector.lacEnergyThreshold) {
            if (!laneChangeEvent) {
                laneChangeWindow = (LinkedList<Float>) lac.clone();
                laneChangeStart = time.getFirst();
            }
            else {
                laneChangeWindow.addAll(lac.subList(Detector.overLap, Detector.windowSize));
            }
            laneChangeEvent = true;
        }
        else if (laneChangeEvent) {
            laneChangeStop = time.get(Detector.overLap);
            laneChangeEvent = false;
            if (LaneChangeDetector.dtwDetection(laneChangeWindow) &&
                    laneChangeStop - laneChangeStart < LaneChangeDetector.MaxDuration &&
                    laneChangeStop - laneChangeStart > LaneChangeDetector.MinDuration ) {
                return new Event(laneChangeStart, laneChangeStop, "lane_change");
            }
        }
        return null;
    }
}
