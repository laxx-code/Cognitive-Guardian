#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(GuardianBridge, RCTEventEmitter)

RCT_EXTERN_METHOD(startDemoFeed)
RCT_EXTERN_METHOD(stopDemoFeed)

@end
