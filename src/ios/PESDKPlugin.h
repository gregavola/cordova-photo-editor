//
//  PESDKPlugin.h
//  PESDKPlugin
//
//  Created by Malte Baumann on 3/15/17.
//
//

#import <Cordova/CDV.h>
@import PhotoEditorSDK;

@interface PESDKPlugin : CDVPlugin

@property (assign) BOOL shouldSave;
@property (assign) BOOL shouldSaveCamera;
@property (assign) BOOL fromCamera;

- (void)present:(CDVInvokedUrlCommand *)command;

@end
