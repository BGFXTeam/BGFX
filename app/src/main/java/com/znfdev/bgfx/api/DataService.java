package com.znfdev.bgfx.api;
import com.znfdev.zetrofit.Call;
import com.znfdev.zetrofit.Callback;
import com.znfdev.zetrofit.Response;
import com.znfdev.zetrofit.Zetrofit;
import com.znfdev.zetrofit.converter.ZsonConverterFactory;

public class DataService {
    
    public static void fetchData(final DataCallback callback) {
        Zetrofit z = new Zetrofit.Builder()
                         .baseUrl("https://raw.githubusercontent.com/BGFXTeam/hub/refs/heads/main/")
                         .addConverterFactory(ZsonConverterFactory.create())
                         .build();
        
        IDataService ds = z.create(IDataService.class);
        
        ds.fetchData().enqueue(new Callback<Data>() {
                public void onResponse(Call<Data> call, Response<Data> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        callback.onSuccsss(response.body());
                    } else {
                        callback.onFailure(new java.io.IOException("Server returned HTTP " + response.code()));
                    }
                }
                public void onFailure(Call<Data> call, Throwable t) {
                    callback.onFailure(t);
                }
            });
        
    }
    
    public static interface DataCallback {
        public void onSuccsss(Data data);
        public void onFailure(Throwable t);
    }
    
}
