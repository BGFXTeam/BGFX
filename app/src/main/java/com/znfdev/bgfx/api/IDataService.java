package com.znfdev.bgfx.api;
import com.znfdev.zetrofit.http.GET;
import com.znfdev.zetrofit.Call;

public interface IDataService {
    
    @GET("bgfx.json")
    Call<Data> fetchData();
    
}
