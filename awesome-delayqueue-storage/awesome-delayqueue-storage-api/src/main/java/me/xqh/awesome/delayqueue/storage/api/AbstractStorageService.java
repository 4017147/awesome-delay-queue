/*
 * Copyright (c) 2018
 * User:qinghua.xu
 * File:AbstractStorageService.java
 * Date:2018/09/28
 */

package me.xqh.awesome.delayqueue.storage.api;

import me.xqh.awesome.delayqueue.common.AwesomeURL;
import me.xqh.awesome.delayqueue.common.exception.AwesomeException;
import me.xqh.awesome.delayqueue.common.exception.JobAlreadyExistException;

/**
 * @author qinghua.xu
 * @date 2018/9/28
 **/
public abstract class AbstractStorageService implements StorageService{
    protected AwesomeURL url;
    @Override
    public boolean addJob(AwesomeJob job) throws AwesomeException {
        if (checkJobRestrict(job)){
            return doAddJob(job);
        }
        return false;
    }
    public AbstractStorageService(AwesomeURL url){
        this.url = url;
    }

    protected abstract boolean doAddJob(AwesomeJob job) throws AwesomeException;
    protected abstract boolean checkJobRestrict(AwesomeJob job) throws AwesomeException;


}
