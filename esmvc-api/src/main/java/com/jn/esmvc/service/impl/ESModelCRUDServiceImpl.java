package com.jn.esmvc.service.impl;

import com.jn.esmvc.model.AbstractESModel;
import com.jn.esmvc.model.utils.ESModels;
import com.jn.esmvc.service.AbstractESModelService;
import com.jn.esmvc.service.ESModelCRUDService;
import com.jn.langx.util.Preconditions;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.jn.esmvc.service.util.ESRequests.logRequestWhenFail;
import static org.elasticsearch.rest.RestStatus.CREATED;
import static org.elasticsearch.rest.RestStatus.OK;

public class ESModelCRUDServiceImpl<MODEL extends AbstractESModel> extends AbstractESModelService<MODEL> implements ESModelCRUDService<MODEL> {
    private static final Logger logger = LoggerFactory.getLogger(ESModelCRUDServiceImpl.class);

    @Override
    public String add(MODEL model) throws IOException {
        Preconditions.checkNotNull(model);
        String index = ESModels.getIndex(model.getClass());
        String type = ESModels.getType(model.getClass());
        String id = ESModels.getId(model);
        IndexRequest request = new IndexRequest(index, type, id);
        request.source(json.toJson(model), XContentType.JSON);
        IndexResponse response;
        try {
            response = client.getRestClient().index(request, client.getRequestOptions());
            RestStatus status = response.status();
            if (status == CREATED || status == OK) {
                return id;
            }
        } catch (Throwable ex) {
            logRequestWhenFail(logger, request, ex);
            throw ex;
        }

        return id;
    }

    @Override
    public MODEL getById(String id) throws IOException {
        Preconditions.checkNotNull(id);
        String index = ESModels.getIndex(modelClass);
        String type = ESModels.getType(modelClass);
        GetRequest request = new GetRequest()
                .index(index)
                .type(type)
                .id(id);

        try {
            GetResponse response = client.getRestClient().get(request, client.getRequestOptions());
            if (response.isExists() && !response.isSourceEmpty()) {
                return asModel(response);
            }
        } catch (Throwable ex) {
            logRequestWhenFail(logger, request, ex);
            throw ex;
        }
        return null;
    }


    @Override
    public boolean removeById(String id) throws IOException {
        Preconditions.checkNotNull(id);
        String index = ESModels.getIndex(modelClass);
        String type = ESModels.getType(modelClass);
        DeleteRequest request = new DeleteRequest()
                .index(index)
                .type(type)
                .id(id);
        DeleteResponse response;
        try {
            response = client.getRestClient().delete(request, client.getRequestOptions());
            RestStatus rs = response.status();
            if (rs == OK) {
                return true;
            }
        } catch (Throwable ex) {
            logRequestWhenFail(logger, request, ex);
            throw ex;
        }
        return false;
    }

    /**
     * update doc
     */
    @Override
    public boolean updateById(String id, MODEL model) throws IOException {
        Preconditions.checkNotNull(model);
        String index = ESModels.getIndex(modelClass);
        String type = ESModels.getType(modelClass);
        UpdateRequest request = new UpdateRequest()
                .index(index)
                .type(type)
                .id(id)
                .doc(json.toJson(model), XContentType.JSON)
                .fetchSource(false);

        UpdateResponse response;
        try {
            response = client.getRestClient().update(request, client.getRequestOptions());
            RestStatus restStatus = response.status();
            if (restStatus == OK) {
                return true;
            }
        } catch (Throwable ex) {
            logRequestWhenFail(logger, request, ex);
            throw ex;
        }
        return false;
    }

    /**
     * 1)update doc
     * 2)get
     */
    @Override
    public MODEL replaceById(String id, MODEL model) throws IOException {
        Preconditions.checkNotNull(model);
        String index = ESModels.getIndex(modelClass);
        String type = ESModels.getType(modelClass);
        UpdateRequest request = new UpdateRequest()
                .index(index)
                .type(type)
                .id(id)
                .doc(json.toJson(model), XContentType.JSON)
                .fetchSource(true);

        UpdateResponse response;
        try {
            response = client.getRestClient().update(request, client.getRequestOptions());
            if (response.getGetResult().isExists() && !response.getGetResult().isSourceEmpty()) {
                return asModel(response.getGetResult());
            }
        } catch (Throwable ex) {
            logRequestWhenFail(logger, request, ex);
            throw ex;
        }
        return null;
    }

    /**
     * 1) doc_as_upsert
     * 2) get
     */
    @Override
    public MODEL merge(MODEL model) throws IOException {
        Preconditions.checkNotNull(model);
        String index = ESModels.getIndex(modelClass);
        String type = ESModels.getType(modelClass);
        String id = ESModels.getId(model);
        UpdateRequest request = new UpdateRequest()
                .index(index)
                .type(type)
                .id(id)
                .doc(json.toJson(model), XContentType.JSON)
                .docAsUpsert(true)
                .fetchSource(true)
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        try {
            UpdateResponse response = client.getRestClient().update(request, client.getRequestOptions());
            if (response.getGetResult().isExists() && !response.getGetResult().isSourceEmpty()) {
                return asModel(response.getGetResult());
            }
        } catch (Throwable ex) {
            logRequestWhenFail(logger, request, ex);
            throw ex;
        }
        return null;
    }

    @Override
    public List<MODEL> getByIds(List<String> ids) throws IOException {
        Preconditions.checkNotNull(ids);
        String index = ESModels.getIndex(modelClass);
        String type = ESModels.getType(modelClass);
        MultiGetRequest request = new MultiGetRequest();
        ids.forEach(id -> request.add(index, type, id));

        List<MODEL> list = new ArrayList<>();
        MultiGetResponse response;
        try {
            response = client.getRestClient().mget(request, client.getRequestOptions());
            response.forEach(multiGetItemResponse -> {
                if (!multiGetItemResponse.isFailed()) {
                    if (multiGetItemResponse.getResponse().isExists() && !multiGetItemResponse.getResponse().isSourceEmpty()) {
                        list.add(asModel(multiGetItemResponse.getResponse()));
                    }
                }
            });
        } catch (IOException ex) {
            logRequestWhenFail(logger, request, ex);
            throw ex;
        }

        return list;
    }

    @Override
    public void bulkMerge(List<MODEL> models) throws IOException {
        Preconditions.checkNotNull(models);

        BulkRequest request = new BulkRequest();

        models.forEach(model -> {
            String index = ESModels.getIndex(model.getClass());
            String type = ESModels.getType(model.getClass());
            String id = ESModels.getId(model);
            UpdateRequest mergeRequest = new UpdateRequest();
            mergeRequest.index(index)
                    .type(type)
                    .id(id)
                    .doc(json.toJson(model), XContentType.JSON)
                    .docAsUpsert(true)
                    .fetchSource(false);
            request.add(mergeRequest);
        });

        BulkResponse bulkResponse;
        try {
            bulkResponse = client.getRestClient().bulk(request, client.getRequestOptions());
            if (bulkResponse.hasFailures()) {
                bulkResponse.buildFailureMessage();
            }
        } catch (IOException ex) {
            logRequestWhenFail(logger, request, ex);
            throw ex;
        }
    }

}
