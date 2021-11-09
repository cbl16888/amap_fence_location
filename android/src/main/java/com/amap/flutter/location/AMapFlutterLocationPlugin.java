package com.amap.flutter.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.amap.api.fence.GeoFence;
import com.amap.api.fence.GeoFenceClient;
import com.amap.api.fence.GeoFenceListener;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.DPoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import static com.amap.api.fence.GeoFenceClient.GEOFENCE_IN;
import static com.amap.api.fence.GeoFenceClient.GEOFENCE_OUT;

/** 高德地图定位sdkFlutterPlugin */
public class AMapFlutterLocationPlugin implements FlutterPlugin, MethodCallHandler,
        EventChannel.StreamHandler {
  private static final String CHANNEL_METHOD_LOCATION = "amap_flutter_location";
  private static final String CHANNEL_STREAM_LOCATION = "amap_flutter_location_stream";

  private Context mContext = null;

  public static EventChannel.EventSink mEventSink = null;


  private Map<String, AMapLocationClientImpl> locationClientMap = new ConcurrentHashMap<String, AMapLocationClientImpl>(8);
  private GeoFenceClient mGeoFenceClient;
  private static final String GEOFENCE_BROADCAST_ACTION = "com.location.apis.geofencedemo.broadcast";
  private Result addResult;

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    String callMethod = call.method;
    switch (call.method) {
      case "setApiKey":
        setApiKey((Map) call.arguments);
        break;
      case "setLocationOption":
        setLocationOption((Map) call.arguments);
        break;
      case "startLocation":
        startLocation((Map) call.arguments);
        break;
      case "stopLocation":
        stopLocation((Map) call.arguments);
        break;
      case "destroy":
        destroy((Map) call.arguments);
        break;
      case "addPolygonRegionForMonitoringWithCoordinates":
        addResult = result;
        Map arguments = (Map)call.arguments;
        List<String> coordinates = (List<String>)arguments.get("coordinates");
        List<DPoint> points = new ArrayList<DPoint>();
        for (String coordinate : coordinates) {
          String[] location = coordinate.split(",");
          points.add(new DPoint(Double.parseDouble(location[0]), Double.parseDouble(location[1])));
        }
        if (null == mGeoFenceClient) {
          //实例化地理围栏客户端
          mGeoFenceClient = new GeoFenceClient(mContext);
          //设置希望侦测的围栏触发行为，默认只侦测用户进入围栏的行为
          //public static final int GEOFENCE_IN 进入地理围栏
          //public static final int GEOFENCE_OUT 退出地理围栏
          //public static final int GEOFENCE_STAYED 停留在地理围栏内10分钟
          mGeoFenceClient.setActivateAction(GEOFENCE_IN|GEOFENCE_OUT);
          //创建回调监听
          GeoFenceListener fenceListenter = new GeoFenceListener() {

            @Override
            public void onGeoFenceCreateFinished(List<GeoFence> list, int errorCode, String string) {
              if (errorCode == GeoFence.ADDGEOFENCE_SUCCESS){//判断围栏是否创建成功
//                tvReult.setText("添加围栏成功!!");
                //geoFenceList是已经添加的围栏列表，可据此查看创建的围栏
                if (null != addResult) {
                  addResult.success(true);
                  addResult = null;
                }
              } else {
//                tvReult.setText("添加围栏失败!!");
                if (null != addResult) {
                  addResult.success(false);
                  addResult = null;
                }
              }
            }
          };
          mGeoFenceClient.setGeoFenceListener(fenceListenter);//设置回调监听
          mGeoFenceClient.createPendingIntent(GEOFENCE_BROADCAST_ACTION);
          BroadcastReceiver mGeoFenceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
              if (intent.getAction().equals(GEOFENCE_BROADCAST_ACTION)) {
                //解析广播内容
                //获取Bundle
                Bundle bundle = intent.getExtras();//获取围栏行为：
                int status = bundle.getInt(GeoFence.BUNDLE_KEY_FENCESTATUS);//获取自定义的围栏标识：
                String customId = bundle.getString(GeoFence.BUNDLE_KEY_CUSTOMID);//获取围栏ID:
//                String fenceId = bundle.getString(GeoFence.BUNDLE_KEY_FENCEID);//获取当前有触发的围栏对象：
                GeoFence fence = bundle.getParcelable(GeoFence.BUNDLE_KEY_FENCE);
                if (null == mEventSink) {
                  return;
                }
                Map<String, Object> result = new LinkedHashMap<String, Object>();
                result.put("pluginKey", "didGeoFencesStatusChangedForRegion");
                if (null != customId) {
                  result.put("customId", customId);
                }
                result.put("fenceStatus", status);
                if (null != fence) {
                  AMapLocation location = fence.getCurrentLocation();
                  if (null != location) {
                    result.put("longitude", location.getLongitude());
                    result.put("latitude", location.getLatitude());
                  }
                  fence.getCurrentLocation().getLatitude();
                }
                mEventSink.success(result);
              }
            }
          };
          IntentFilter filter = new IntentFilter(
                  ConnectivityManager.CONNECTIVITY_ACTION);
          filter.addAction(GEOFENCE_BROADCAST_ACTION);
          mContext.registerReceiver(mGeoFenceReceiver, filter);
        }
        mGeoFenceClient.addGeoFence(points, (String)arguments.get("customID"));
        break;
      case "updatePrivacyShow":
        Map callArguments = (Map)call.arguments;
        boolean isContains = (int)callArguments.get("isContains") == 1;
        boolean isShow = (int)callArguments.get("isShow") == 1;
        AMapLocationClient.updatePrivacyShow(mContext,isContains,isShow);
        result.success(true);
        break;
      case "updatePrivacyAgree":
        Map agreeArguments = (Map)call.arguments;
        boolean isAgree = (int)agreeArguments.get("isAgree") == 1;
        AMapLocationClient.updatePrivacyAgree(mContext,isAgree);
        result.success(true);
        break;
      case "removeGeoFenceRegionsWithCustomID":
        if (null != mGeoFenceClient) {
          mGeoFenceClient.removeGeoFence();
        }
        result.success(true);
        break;
      default:
        result.notImplemented();
        break;

    }
  }

  @Override
  public void onListen(Object o, EventChannel.EventSink eventSink) {
    mEventSink = eventSink;
  }

  @Override
  public void onCancel(Object o) {
    for (Map.Entry<String, AMapLocationClientImpl> entry : locationClientMap.entrySet()) {
      entry.getValue().stopLocation();
    }
  }

  /**
   * 开始定位
   */
  private void startLocation(Map argsMap) {
    AMapLocationClientImpl locationClientImp = getLocationClientImp(argsMap);
    if (null != locationClientImp) {
      locationClientImp.startLocation();
    }
  }


  /**
   * 停止定位
   */
  private void stopLocation(Map argsMap) {
    AMapLocationClientImpl locationClientImp = getLocationClientImp(argsMap);
    if (null != locationClientImp) {
      locationClientImp.stopLocation();
    }
  }

  /**
   * 销毁
   *
   * @param argsMap
   */
  private void destroy(Map argsMap) {
    AMapLocationClientImpl locationClientImp = getLocationClientImp(argsMap);
    if (null != locationClientImp) {
      locationClientImp.destroy();

      locationClientMap.remove(getPluginKeyFromArgs(argsMap));
    }
  }

  /**
   * 设置apikey
   *
   * @param apiKeyMap
   */
  private void setApiKey(Map apiKeyMap) {
    if (null != apiKeyMap) {
      if (apiKeyMap.containsKey("android")
              && !TextUtils.isEmpty((String) apiKeyMap.get("android"))) {
//        AMapLocationClient.setApiKey((String) apiKeyMap.get("android"));
      }
    }
  }

  /**
   * 设置定位参数
   *
   * @param argsMap
   */
  private void setLocationOption(Map argsMap) {
    AMapLocationClientImpl locationClientImp = getLocationClientImp(argsMap);
    if (null != locationClientImp) {
      locationClientImp.setLocationOption(argsMap);
    }
  }


  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {
    if (null == mContext) {
      mContext = binding.getApplicationContext();

      /**
       * 方法调用通道
       */
      final MethodChannel channel = new MethodChannel(binding.getBinaryMessenger(), CHANNEL_METHOD_LOCATION);
      channel.setMethodCallHandler(this);

      /**
       * 回调监听通道
       */
      final EventChannel eventChannel = new EventChannel(binding.getBinaryMessenger(), CHANNEL_STREAM_LOCATION);
      eventChannel.setStreamHandler(this);
    }

  }

  @Override
  public void onDetachedFromEngine(FlutterPluginBinding binding) {
    for (Map.Entry<String, AMapLocationClientImpl> entry : locationClientMap.entrySet()) {
      entry.getValue().destroy();
    }
  }

  private AMapLocationClientImpl getLocationClientImp(Map argsMap) {
    if (null == locationClientMap) {
      locationClientMap = new ConcurrentHashMap<String, AMapLocationClientImpl>(8);
    }

    String pluginKey = getPluginKeyFromArgs(argsMap);
    if (TextUtils.isEmpty(pluginKey)) {
      return null;
    }

    if (!locationClientMap.containsKey(pluginKey)) {
      AMapLocationClientImpl locationClientImp = new AMapLocationClientImpl(mContext, pluginKey, mEventSink);
      locationClientMap.put(pluginKey, locationClientImp);
    }
    return locationClientMap.get(pluginKey);
  }

  private String getPluginKeyFromArgs(Map argsMap) {
    String pluginKey = null;
    try {
      if (null != argsMap) {
        pluginKey = (String) argsMap.get("pluginKey");
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
    return pluginKey;
  }
}
