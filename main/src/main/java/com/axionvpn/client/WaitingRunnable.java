package com.axionvpn.client;

public abstract class WaitingRunnable {
    public Exception backgroundException = null;
    abstract public void inBackground() throws Exception; // required
    public void onCompleted() { }                         // optional
}