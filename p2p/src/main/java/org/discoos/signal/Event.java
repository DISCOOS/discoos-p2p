package org.discoos.signal;

public final class Event {

    private final Object mSignal;
    private final Object mSource;
    private final Observable mObservable;

    /**
     * Constructs a new instance of this class.
     *
     * @param signal the signal
     * @param observable the observable object
     * @param source the object which fired the event
     */
    public Event(Object signal, Observable observable, Object source) {
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

    public Observable getObservable() {
        return mObservable;
    }

}
