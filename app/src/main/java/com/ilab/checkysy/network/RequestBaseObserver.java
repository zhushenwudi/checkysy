package com.ilab.checkysy.network;

import io.reactivex.Observer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;

public abstract class RequestBaseObserver<V> implements Observer<V> {

    protected RequestBaseObserver() {
    }

    @Override
    public void onSubscribe(@NonNull Disposable d) {
    }

    @Override
    public void onNext(V t) {
        onSuccess(t);
    }

    @Override
    public void onError(Throwable e) {
    }

    @Override
    public void onComplete() {
    }

    protected void onProgress(String percent) {
    }

    protected abstract void onSuccess(V t);

}
