package com.reactlibrary;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.applozic.mobicomkit.Applozic;
import com.applozic.mobicomkit.ApplozicClient;
import com.applozic.mobicomkit.api.conversation.Message;
import com.applozic.mobicomkit.api.conversation.MessageBuilder;
import com.applozic.mobicomkit.api.conversation.MobiComConversationService;
import com.applozic.mobicomkit.exception.ApplozicException;
import com.applozic.mobicomkit.contact.AppContactService;
import com.applozic.mobicomkit.listners.MediaUploadProgressHandler;
import com.applozic.mobicomkit.uiwidgets.ApplozicSetting;
import com.applozic.mobicomkit.api.account.register.RegistrationResponse;
import com.applozic.mobicomkit.api.account.user.MobiComUserPreference;
import com.applozic.mobicomkit.api.account.user.User;
import com.applozic.mobicomkit.api.account.user.UserClientService;
import com.applozic.mobicomkit.api.account.user.UserLoginTask;
import com.applozic.mobicomkit.api.account.user.PushNotificationTask;
import com.applozic.mobicomkit.api.conversation.database.MessageDatabaseService;
import com.applozic.mobicomkit.api.people.ChannelInfo;
import com.applozic.mobicomkit.channel.service.ChannelService;
import com.applozic.mobicomkit.uiwidgets.async.AlGroupInformationAsyncTask;
import com.applozic.mobicomkit.uiwidgets.async.ApplozicChannelAddMemberTask;
import com.applozic.mobicomkit.uiwidgets.conversation.ConversationUIService;
import com.applozic.mobicomkit.uiwidgets.conversation.activity.ConversationActivity;
import com.applozic.mobicommons.file.FileUtils;
import com.applozic.mobicommons.json.GsonUtils;
import com.applozic.mobicommons.people.channel.Channel;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.applozic.mobicomkit.feed.AlResponse;
import com.applozic.mobicomkit.uiwidgets.async.ApplozicChannelRemoveMemberTask;
import com.applozic.mobicommons.people.contact.Contact;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ApplozicChatModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    public ApplozicChatModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "ApplozicChat";
    }

    @ReactMethod
    public void login(final ReadableMap config, final Callback callback) {
        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            callback.invoke("Activity doesn't exist", null);
            return;
        }

        UserLoginTask.TaskListener listener = new UserLoginTask.TaskListener() {
            @Override
            public void onSuccess(RegistrationResponse registrationResponse, Context context) {
                //After successful registration with Applozic server the callback will come here
                if (MobiComUserPreference.getInstance(currentActivity).isRegistered()) {
                    String json = GsonUtils.getJsonFromObject(registrationResponse, RegistrationResponse.class);
                    callback.invoke(null, json);

                    PushNotificationTask pushNotificationTask = null;

                    PushNotificationTask.TaskListener listener = new PushNotificationTask.TaskListener() {
                        public void onSuccess(RegistrationResponse registrationResponse) {

                        }

                        @Override
                        public void onFailure(RegistrationResponse registrationResponse, Exception exception) {
                        }
                    };
                    String registrationId = Applozic.getInstance(context).getDeviceRegistrationId();
                    pushNotificationTask = new PushNotificationTask(registrationId, listener, currentActivity);
                    pushNotificationTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    ;
                } else {
                    String json = GsonUtils.getJsonFromObject(registrationResponse, RegistrationResponse.class);
                    callback.invoke(json, null);
                }

            }

            @Override
            public void onFailure(RegistrationResponse registrationResponse, Exception exception) {
                //If any failure in registration the callback  will come here
                callback.invoke(exception != null ? exception.toString() : "error", registrationResponse != null ? GsonUtils.getJsonFromObject(registrationResponse, RegistrationResponse.class) : "Unknown error occurred");
            }
        };

        User user = (User) GsonUtils.getObjectFromJson(GsonUtils.getJsonFromObject(config.toHashMap(), HashMap.class), User.class);
        new UserLoginTask(user, listener, currentActivity).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @ReactMethod
    public void openChat() {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            Log.i("OpenChat Error ", "Activity doesn't exist");
            return;
        }

        Intent intent = new Intent(currentActivity, ConversationActivity.class);
        currentActivity.startActivity(intent);
    }

    @ReactMethod
    public void openChatWithUser(String userId) {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            Log.i("open ChatWithUser  ", "Activity doesn't exist");
            return;
        }

        Intent intent = new Intent(currentActivity, ConversationActivity.class);

        if (userId != null) {

            intent.putExtra(ConversationUIService.USER_ID, userId);
            intent.putExtra(ConversationUIService.TAKE_ORDER, true);

        }
        currentActivity.startActivity(intent);
    }

    @ReactMethod
    public void openChatWithGroup(Integer groupId, final Callback callback) {

        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            callback.invoke("Activity doesn't exist", null);
            return;
        }

        if (groupId != null) {

            AlGroupInformationAsyncTask.GroupMemberListener taskListener = new AlGroupInformationAsyncTask.GroupMemberListener() {
                @Override
                public void onSuccess(Channel channel, Context context) {
                    Intent chatIntent = new Intent(context, ConversationActivity.class);
                    chatIntent.putExtra(ConversationUIService.GROUP_ID, channel.getKey());
                    chatIntent.putExtra(ConversationUIService.GROUP_NAME, channel.getName());
                    chatIntent.putExtra(ConversationUIService.TAKE_ORDER, true);
                    context.startActivity(chatIntent);
                    callback.invoke(null, "success");
                }

                @Override
                public void onFailure(Channel channel, Exception e, Context context) {
                    callback.invoke("Failed to launch group chat", null);
                }
            };
            AlGroupInformationAsyncTask groupInfoTask = new AlGroupInformationAsyncTask(currentActivity, groupId, taskListener);
            groupInfoTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        } else {
            callback.invoke("unable to launch group chat, check your groupId/ClientGroupId", null);
        }

    }

    @ReactMethod
    public void openChatWithClientGroupId(String clientGroupId, final Callback callback) {

        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            callback.invoke("Activity doesn't exist", null);
            return;
        }

        if (TextUtils.isEmpty(clientGroupId)) {
            callback.invoke("unable to launch group chat, check your groupId/ClientGroupId", null);
        } else {

            AlGroupInformationAsyncTask.GroupMemberListener taskListener = new AlGroupInformationAsyncTask.GroupMemberListener() {
                @Override
                public void onSuccess(Channel channel, Context context) {
                    Intent chatIntent = new Intent(context, ConversationActivity.class);
                    chatIntent.putExtra(ConversationUIService.GROUP_ID, channel.getKey());
                    chatIntent.putExtra(ConversationUIService.GROUP_NAME, channel.getName());
                    chatIntent.putExtra(ConversationUIService.TAKE_ORDER, true);
                    context.startActivity(chatIntent);
                    callback.invoke(null, "success");
                }

                @Override
                public void onFailure(Channel channel, Exception e, Context context) {
                    callback.invoke("Failed to launch group chat", null);
                }
            };
            AlGroupInformationAsyncTask groupInfoTask = new AlGroupInformationAsyncTask(currentActivity, clientGroupId, taskListener);
            groupInfoTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    @ReactMethod
    public void logoutUser(final Callback callback) {

        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            callback.invoke("Activity doesn't exist");
            return;
        }

        new UserClientService(currentActivity).logout();
        callback.invoke(null, "success");
    }

    //============================================ Group Method ==============================================

    /***
     *
     * @param config
     * @param callback
     */
    @ReactMethod
    public void createGroup(final ReadableMap config, final Callback callback) {

        final Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {

            callback.invoke("Activity doesn't exist", null);
            return;

        }

        if (TextUtils.isEmpty(config.getString("groupName"))) {

            callback.invoke("Group name must be passed", null);
            return;
        }

        List<String> channelMembersList = (List<String>) (Object) (config.getArray("groupMemberList").toArrayList());

        final ChannelInfo channelInfo = new ChannelInfo(config.getString("groupName"), channelMembersList);

        if (!TextUtils.isEmpty(config.getString("clientGroupId"))) {
            channelInfo.setClientGroupId(config.getString("clientGroupId"));
        }
        if (config.hasKey("type")) {
            channelInfo.setType(config.getInt("type")); //group type
        } else {
            channelInfo.setType(Channel.GroupType.PUBLIC.getValue().intValue()); //group type
        }
        channelInfo.setImageUrl(config.getString("imageUrl")); //pass group image link URL
        Map<String, String> metadata = (HashMap<String, String>) (Object) (config.getMap("metadata").toHashMap());
        channelInfo.setMetadata(metadata);

        new Thread(new Runnable() {
            @Override
            public void run() {

                AlResponse alResponse = ChannelService.getInstance(currentActivity).createChannel(channelInfo);
                Channel channel = null;
                if (alResponse.isSuccess()) {
                    channel = (Channel) alResponse.getResponse();
                }
                if (channel != null && channel.getKey() != null) {
                    callback.invoke(null, channel.getKey());
                } else {
                    if (alResponse.getResponse() != null) {
                        callback.invoke(GsonUtils.getJsonFromObject(alResponse.getResponse(), List.class), null);
                    } else if (alResponse.getException() != null) {
                        callback.invoke(alResponse.getException().getMessage(), null);
                    }
                }
            }
        }).start();
    }

    /**
     * @param config
     * @param callback
     */
    @ReactMethod
    public void addMemberToGroup(final ReadableMap config, final Callback callback) {

        final Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {

            callback.invoke("Activity doesn't exist", null);
            return;

        }

        Integer channelKey = null;
        String userId = config.getString("userId");

        if (!TextUtils.isEmpty(config.getString("clientGroupId"))) {
            Channel channel = ChannelService.getInstance(currentActivity).getChannelByClientGroupId(config.getString("clientGroupId"));
            channelKey = channel != null ? channel.getKey() : null;

        } else if (!TextUtils.isEmpty(config.getString("groupId"))) {
            channelKey = Integer.parseInt(config.getString("groupId"));
        }

        if (channelKey == null) {
            callback.invoke("groupId/clientGroupId not passed", null);
            return;
        }

        ApplozicChannelAddMemberTask.ChannelAddMemberListener channelAddMemberListener = new ApplozicChannelAddMemberTask.ChannelAddMemberListener() {
            @Override
            public void onSuccess(String response, Context context) {
                //Response will be "success" if user is added successfully
                Log.i("ApplozicChannelMember", "Add Response:" + response);
                callback.invoke(null, response);
            }

            @Override
            public void onFailure(String response, Exception e, Context context) {
                callback.invoke(response, null);

            }
        };

        ApplozicChannelAddMemberTask applozicChannelAddMemberTask = new ApplozicChannelAddMemberTask(currentActivity, channelKey, userId, channelAddMemberListener);//pass channel key and userId whom you want to add to channel
        applozicChannelAddMemberTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }


    /**
     * @param config
     * @param callback
     */
    @ReactMethod
    public void removeUserFromGroup(final ReadableMap config, final Callback callback) {

        final Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {

            callback.invoke("Activity doesn't exist", null);
            return;

        }

        Integer channelKey = null;
        String userId = config.getString("userId");

        if (!TextUtils.isEmpty(config.getString("clientGroupId"))) {
            Channel channel = ChannelService.getInstance(currentActivity).getChannelByClientGroupId(config.getString("clientGroupId"));
            channelKey = channel != null ? channel.getKey() : null;

        } else if (!TextUtils.isEmpty(config.getString("groupId"))) {
            channelKey = Integer.parseInt(config.getString("groupId"));
        }

        if (channelKey == null) {
            callback.invoke("groupId/clientGroupId not passed", null);
            return;
        }

        ApplozicChannelRemoveMemberTask.ChannelRemoveMemberListener channelRemoveMemberListener = new ApplozicChannelRemoveMemberTask.ChannelRemoveMemberListener() {
            @Override
            public void onSuccess(String response, Context context) {
                callback.invoke(null, response);
                //Response will be "success" if user is removed successfully
                Log.i("ApplozicChannel", "remove member response:" + response);
            }

            @Override
            public void onFailure(String response, Exception e, Context context) {
                callback.invoke(response, null);

            }
        };

        ApplozicChannelRemoveMemberTask applozicChannelRemoveMemberTask = new ApplozicChannelRemoveMemberTask(currentActivity, channelKey, userId, channelRemoveMemberListener);//pass channelKey and userId whom you want to remove from channel
        applozicChannelRemoveMemberTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    //======================================================================================================

    @ReactMethod
    public void getUnreadCountForUser(String userId, final Callback callback) {

        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            callback.invoke("Activity doesn't exist", null);
            return;
        }

        int contactUnreadCount = new MessageDatabaseService(getCurrentActivity()).getUnreadMessageCountForContact(userId);
        callback.invoke(null, contactUnreadCount);

    }

    @ReactMethod
    public void getUnreadCountForChannel(ReadableMap config, final Callback callback) {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            callback.invoke("Activity doesn't exist", null);
            return;
        }

        AlGroupInformationAsyncTask.GroupMemberListener listener = new AlGroupInformationAsyncTask.GroupMemberListener() {
            @Override
            public void onSuccess(Channel channel, Context context) {
                if (channel == null) {
                    callback.invoke("Channel dose not exist", null);
                } else {
                    callback.invoke(null, new MessageDatabaseService(context).getUnreadMessageCountForChannel(channel.getKey()));
                }
            }

            @Override
            public void onFailure(Channel channel, Exception e, Context context) {
                callback.invoke("Some error occurred : " + (e != null ? e.getMessage() : ""));
            }
        };

        if (config != null && config.hasKey("clientGroupId")) {
            new AlGroupInformationAsyncTask(currentActivity, config.getString("clientGroupId"), listener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else if (config != null && config.hasKey("groupId")) {
            new AlGroupInformationAsyncTask(currentActivity, config.getInt("groupId"), listener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            callback.invoke("Invalid data sent");
        }
    }

    @ReactMethod
    public void setContactsGroupNameList(ReadableMap config) {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            return;
        }
        List<String> contactGroupIdList = Arrays.asList((String[]) GsonUtils.getObjectFromJson(config.getString("contactGroupNameList"), String[].class));
        Set<String> contactGroupIdsSet = new HashSet<String>(contactGroupIdList);
        MobiComUserPreference.getInstance(currentActivity).setIsContactGroupNameList(true);
        MobiComUserPreference.getInstance(currentActivity).setContactGroupIdList(contactGroupIdsSet);
    }

    @ReactMethod
    public void totalUnreadCount(final Callback callback) {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            callback.invoke("Activity doesn't exist", null);
            return;
        }

        int totalUnreadCount = new MessageDatabaseService(currentActivity).getTotalUnreadCount();
        callback.invoke(null, totalUnreadCount);

    }

    @ReactMethod
    public void isUserLogIn(final Callback successCallback) {
        Activity currentActivity = getCurrentActivity();
        MobiComUserPreference mobiComUserPreference = MobiComUserPreference.getInstance(currentActivity);
        successCallback.invoke(mobiComUserPreference.isLoggedIn());
    }

    @ReactMethod
    public void sendMessage(final String messageJson, final Callback callback) {
        final Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            callback.invoke("error", "Activity doesn't exists..");
            return;
        }

        final Message message = (Message) GsonUtils.getObjectFromJson(messageJson, Message.class);

        if (message == null) {
            callback.invoke("error", "Unable to parse data to Applozic Message");
            return;
        }

        new MessageBuilder(currentActivity).setMessageObject(message).send(new MediaUploadProgressHandler() {
            @Override
            public void onUploadStarted(ApplozicException e, String oldMessageKey) {

            }

            @Override
            public void onProgressUpdate(int percentage, ApplozicException e, String oldMessageKey) {

            }

            @Override
            public void onCancelled(ApplozicException e, String oldMessageKey) {

            }

            @Override
            public void onCompleted(ApplozicException e, String oldMessageKey) {

            }

            @Override
            public void onSent(Message message, String oldMessageKey) {
                callback.invoke("Message sent", oldMessageKey, GsonUtils.getJsonFromObject(message, Message.class));
            }
        });
    }

    @ReactMethod
    public void addContacts(String contactJson, Callback callback) {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            return;
        }
        try {
            if (!TextUtils.isEmpty(contactJson)) {
                AppContactService appContactService = new AppContactService(currentActivity);
                Contact[] contactList = (Contact[]) GsonUtils.getObjectFromJson(contactJson, Contact[].class);
                for (Contact contact : contactList) {
                    appContactService.upsert(contact);
                }
                callback.invoke("Success", "Contacts inserted");
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.invoke("Error", e.getMessage());
            }
        }
    }

    @ReactMethod
    public void openChatWithUserName(String userId, String userName) {
        Activity currentActivity = getCurrentActivity();

        if (currentActivity == null) {
            return;
        }

        Intent intent = new Intent(currentActivity, ConversationActivity.class);

        if (userId != null) {

            intent.putExtra(ConversationUIService.USER_ID, userId);
            intent.putExtra(ConversationUIService.TAKE_ORDER, true);

        }
        if (userName != null && userName != "") {
            intent.putExtra(ConversationUIService.DISPLAY_NAME, userName);
        }
        currentActivity.startActivity(intent);
    }

    @ReactMethod
    public void hideCreateGroupIcon(boolean hide) {
        Activity currentActivity = getCurrentActivity();

        if (hide) {
            ApplozicSetting.getInstance(currentActivity).hideStartNewGroupButton();
        } else {
            ApplozicSetting.getInstance(currentActivity).showStartNewGroupButton();
        }
    }

    @ReactMethod
    public void showOnlyMyContacts(boolean showOnlyMyContacts) {
        Activity currentActivity = getCurrentActivity();

        if (showOnlyMyContacts) {
            ApplozicClient.getInstance(currentActivity).enableShowMyContacts();
        } else {
            ApplozicClient.getInstance(currentActivity).disableShowMyContacts();
        }
    }

    @ReactMethod
    public void hideChatListOnNotification() {
        Activity currentActivity = getCurrentActivity();
        ApplozicClient.getInstance(currentActivity).hideChatListOnNotification();
    }

    @ReactMethod
    public void hideGroupSubtitle() {

    }

    @ReactMethod
    public void setAttachmentType(ReadableMap config) {
        Activity currentActivity = getCurrentActivity();
        Map<FileUtils.GalleryFilterOptions, Boolean> options = new HashMap<>();

        if (config.hasKey("allFiles")) {
            options.put(FileUtils.GalleryFilterOptions.ALL_FILES, config.getBoolean("allFiles"));
        }

        if (config.hasKey("imageVideo")) {
            options.put(FileUtils.GalleryFilterOptions.IMAGE_VIDEO, config.getBoolean("imageVideo"));
        }

        if (config.hasKey("image")) {
            options.put(FileUtils.GalleryFilterOptions.IMAGE_ONLY, config.getBoolean("image"));
        }

        if (config.hasKey("audio")) {
            options.put(FileUtils.GalleryFilterOptions.AUDIO_ONLY, config.getBoolean("audio"));
        }

        if (config.hasKey("video")) {
            options.put(FileUtils.GalleryFilterOptions.VIDEO_ONLY, config.getBoolean("video"));
        }

        ApplozicSetting.getInstance(currentActivity).setGalleryFilterOptions(options);
    }

    @ReactMethod
    public void setAttachmentOptions(ReadableMap config) {
        Activity currentActivity = getCurrentActivity();
        Map<String, Boolean> options = new HashMap<>();

        for (Map.Entry<String, Object> item : config.toHashMap().entrySet()) {
            options.put(item.getKey(), (Boolean) item.getValue());
        }
        ApplozicSetting.getInstance(currentActivity).setAttachmentOptions(options);
    }

    @ReactMethod
    public void getLatestMessagesGroupByPeople(Promise promise) {
        Context context = this.getReactApplicationContext();
        MobiComConversationService mobiComConversationService = new MobiComConversationService(context);

        try {
            List<Message> messages = mobiComConversationService.getLatestMessagesGroupByPeople();

            //callback.invoke(null, messages);
            WritableMap output = Arguments.createMap();

            if (messages != null) {
                WritableArray message = Arguments.createArray();
                WritableArray groupFeeds = Arguments.createArray();

                for (Message msg : messages) {
                    message.pushMap(messageToMap(msg));

                    if (msg.isGroupMessage()) {
                        int groupId = msg.getGroupId();

                        Channel channel = ChannelService.getInstance(context).getChannelInfo(groupId);
                        if (channel != null) {
                            groupFeeds.pushMap(channelToMap(channel));
                        }
                    } else {
                        //msg.getSuUserKeyString()
                    }
                }

                output.putArray("message", message);
                output.putArray("groupFeeds", groupFeeds);
            }

            promise.resolve(output);
        } catch (Exception ex) {
            promise.reject(ex);
        }
    }

    @Override
    public void onActivityResult(Activity activity, final int requestCode, final int resultCode, final Intent intent) {
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    private WritableMap channelToMap(Channel channel) {
        WritableMap map = Arguments.createMap();

        // TODO

        return map;
    }

    private WritableMap messageToMap(Message msg) {
        WritableMap map = Arguments.createMap();

        map.putDouble("createdAtTime", msg.getCreatedAtTime());
        map.putString("to", msg.getTo());
        map.putString("message", msg.getMessage());
        map.putString("key", msg.getKeyString());
        map.putString("deviceKey", msg.getDeviceKeyString());
        map.putString("userKey", msg.getSuUserKeyString());
        map.putString("emailIds", msg.getEmailIds());
        map.putBoolean("shared", msg.isShared());
        map.putBoolean("sent", msg.isSent());
        map.putBoolean("delivered", msg.getDelivered());
        map.putInt("type", msg.getType());
        map.putBoolean("storeOnDevice", msg.isStoreOnDevice());
        map.putString("contactIds", msg.getContactIds());
        map.putInt("groupId", msg.getGroupId());
        map.putBoolean("sendToDevice", msg.isSendToDevice());
        map.putDouble("scheduledAt", msg.getScheduledAt());
        map.putInt("source", msg.getSource());
        map.putInt("timeToLive", msg.getTimeToLive());
        map.putBoolean("sentToServer", msg.isSentToServer());
        map.putString("fileMetaKey", msg.getFileMetaKeyStrings());
        map.putArray("filePaths", convertToWritableArray(msg.getFilePaths()));
        map.putString("pairedMessageKey", msg.getPairedMessageKeyString());
        map.putDouble("sentMessageTimeAtServer", msg.getSentMessageTimeAtServer());
        map.putBoolean("canceled", msg.isCanceled());
        map.putString("clientGroupId", msg.getClientGroupId());
        map.putString("fileMeta", msg.getFileMetaKeyStrings());
        map.putDouble("messageId", msg.getMessageId());
        map.putBoolean("read", msg.isRead());
        map.putBoolean("attDownloadInProgress", msg.isAttDownloadInProgress());
        map.putString("applicationId", msg.getApplicationId());
        map.putInt("conversationId", msg.getConversationId());
        map.putString("topicId", msg.getTopicId());
        map.putBoolean("connected", msg.isConnected());
        map.putInt("contentType", msg.getContentType());
        map.putMap("metadata", convertToWritableMap(msg.getMetadata()));
        map.putInt("status", msg.getStatus());

        return map;
    }

    private WritableMap convertToWritableMap(Map<String, String> map) {
        WritableMap output = Arguments.createMap();

        if (map != null) {
            for (String key:map.keySet()) {
                output.putString(key, map.get(key));
            }
        }

        return output;
    }

    private WritableArray convertToWritableArray(List<String> list) {
        WritableArray output = Arguments.createArray();

        if (list != null) {
            for (String value:list) {
                output.pushString(value);
            }
        }

        return output;
    }
}
