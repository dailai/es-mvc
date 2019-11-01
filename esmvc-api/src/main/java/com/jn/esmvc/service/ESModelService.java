/*
 *  Copyright (c) 2016
 *  BES Software Corporation.
 *  All Rights Reserved
 *      Revision History:
 *                                 Modification       Tracking
 *  Author (Email ID)              Date               Number               Description
 *  -------------------------------------------------------------------------------------------
 *  jinuo.fang                     2019-04-15                              Initial version
 *
 */

package com.jn.esmvc.service;


import com.jn.esmvc.model.AbstractESModel;

public interface ESModelService<MODEL extends AbstractESModel> extends ESModelCRUDService<MODEL>, ESModelSearchService<MODEL>{
}