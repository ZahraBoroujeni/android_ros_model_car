#include <stdarg.h>
#include <stdio.h>
#include <sstream>
#include <map>
#include <string.h>
#include <errno.h>
#include <vector>
#include <set>
#include <fstream>
#include <android/log.h>
#include <string.h>
#include <jni.h>
// %Tag(FULL_TEXT)%

// %Tag(ROS_HEADER)%
#include "ros/ros.h"
#include <std_msgs/String.h>
#include <std_msgs/Int16.h>
// %EndTag(ROS_HEADER)%

// %Tag(ANDROID_NATIVE_HEADER)%
#include <com_ros_sampleapp_sampleApp.h>
// %EndTag(ANDROID_NATIVE_HEADER)%

int loop_count_ = 0;
ros::Publisher speed_pub;
ros::Publisher steering_pub;

void log(const char *msg, ...) {
    va_list args;
    va_start(args, msg);
    __android_log_vprint(ANDROID_LOG_INFO, "ROSCPP_NDK_EXAMPLE", msg, args);
    va_end(args);
}


// from android samples
/* return current time in seconds */
static double now(void) {

  struct timespec res;
  clock_gettime(CLOCK_REALTIME, &res);
  return res.tv_sec + (double) res.tv_nsec / 1e9;

}

#define LASTERR strerror(errno)

// %Tag(CHATTER_CALLBACK)%

void Java_com_ros_sampleapp_sampleApp_changeSpeed(JNIEnv *env,jobject thiz,jint manualSpeed)
{
    std_msgs::Int16 manual_speed;
    manual_speed.data=manualSpeed;
    speed_pub.publish(manual_speed);
}
void Java_com_ros_sampleapp_sampleApp_changeSteering(JNIEnv *env,jobject thiz,jint manualSteering)
{
    std_msgs::Int16 manual_steering;
    manual_steering.data=manualSteering;
    steering_pub.publish(manual_steering);
}
// %Tag(MAIN)%
void
Java_com_ros_sampleapp_sampleApp_init(JNIEnv *env,
                                                 jobject thiz)
{
        

    int argc = 3;
    // TODO: don't hardcode ip addresses
    // %Tag(CONF_ARGS)%
    char *argv[] = {"nothing_important" , "__master:=http://192.168.43.102:11311", "__ip:=192.168.43.1"};
    // %EndTag(CONF_ARGS)%
    //strcpy(argv[0], 'nothing_important');
    //argv[1] = '__master:=http://10.52.90.103:11311';
    //argv[2] = '__ip:=10.52.90.246';
    //argv[3] = '__hostname:=10.52.90.246';
    log("GOING TO ROS INIT");

    for(int i = 0; i < argc; i++){
        log(argv[i]);
    }

    // %Tag(ROS_INIT)%
    ros::init(argc, &argv[0], "android_ndk_native_cpp");
    // %EndTag(ROS_INIT)%

    log("GOING TO NODEHANDLE");

    // %Tag(ROS_MASTER)%
    std::string master_uri = ros::master::getURI();

    if(ros::master::check()){
        log("ROS MASTER IS UP!");
    } else {
        log("NO ROS MASTER.");
    }
    log(master_uri.c_str());

    ros::NodeHandle n;
    // %EndTag(ROS_MASTER)%


    log("GOING TO PUBLISHER");

    // %Tag(ROS_CONF_SUB_PUB)%
    speed_pub = n.advertise<std_msgs::Int16>("/manual_control/speed", 2);
    steering_pub = n.advertise<std_msgs::Int16>("/manual_control/steering", 2);
    ros::WallRate loop_rate(100);
    // %EndTag(ROS_CONF_SUB_PUB)%

    log("GOING TO SPIN");
    ros::spinOnce();
    // %Tag(ROS_SPIN)%
    // while(ros::ok()){
    //     
    //     loop_rate.sleep();
    // }
    //return env->NewStringUTF("Hello World JNI!");

}
// JNIEXPORT jstring JNICALL (JNIEnv* env, jclass clazz) {

//     //return (*env)->NewStringUTF(env, “Hello World!”);
//     




    // Make sure glue isn't stripped
    //app_dummy();
    // SLresult result;
    // (void)result;
    // int argc = 3;
    // // TODO: don't hardcode ip addresses
    // // %Tag(CONF_ARGS)%
    // char *argv[] = {"nothing_important" , "__master:=http://192.168.43.102:11311", "__ip:=192.168.43.1"};
    // // %EndTag(CONF_ARGS)%
    // //strcpy(argv[0], 'nothing_important');
    // //argv[1] = '__master:=http://10.52.90.103:11311';
    // //argv[2] = '__ip:=10.52.90.246';
    // //argv[3] = '__hostname:=10.52.90.246';


    // // %Tag(ROS_INIT)%
    // ros::init(argc, &argv[0], "android_ndk_native_cpp");
    // // %EndTag(ROS_INIT)%

    // // %Tag(ROS_MASTER)%
    // std::string master_uri = ros::master::getURI();


    // ros::NodeHandle n;
    // // %EndTag(ROS_MASTER)%



    // // %Tag(ROS_CONF_SUB_PUB)%
    // chatter_pub = n.advertise<std_msgs::String>("a_chatter", 1000);
    // ros::Subscriber sub = n.subscribe("chatter", 1000, chatterCallback);
    // ros::WallRate loop_rate(100);
    // // %EndTag(ROS_CONF_SUB_PUB)%



    // // %Tag(ROS_SPIN)%
    // while(ros::ok()){
    //     ros::spinOnce();
    //     loop_rate.sleep();
    // }
    // %EndTag(ROS_SPIN)%
//}
// %EndTag(MAIN)%
// %EndTag(FULL_TEXT)%
