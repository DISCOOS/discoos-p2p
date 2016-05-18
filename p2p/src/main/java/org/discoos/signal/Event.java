package org.discoos.signal;

public class Event {

    private final Object mSignal;
    private final Object mSource;
    private final Object mObservable;

    /**
     * Constructs a new instance of this class.
     *
     * @param signal the signal
     * @param source the object which fired the event
     */
    public Event(Object signal, Object source) {
        this(signal, source, null);
    }

    /**
     * Constructs a new instance of this class.
     *
     * @param signal the signal
     * @param observable the observable object
     * @param source the object which fired the event
     */
    public Event(Object signal, Object source, Object observable) {
        mSignal = signal;
        mSource = source;
        mObservable = observable;
    }

    public Object getSignal() {
        return mSignal;
    }


    public Object getSource() {
        return mSource;
    }

    public Object getObservable() {
        return mObservable;
    }



}
