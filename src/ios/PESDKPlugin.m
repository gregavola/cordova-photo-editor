//
//  PESDKPlugin.m
//  PESDKPlugin
//
//  Created by Malte Baumann on 3/15/17.
//
//

#import <Photos/Photos.h>
#import <objc/message.h>
#import "PESDKPlugin.h"
@import PhotoEditorSDK;

@interface PESDKPlugin () <PESDKPhotoEditViewControllerDelegate>
@property(strong) CDVInvokedUrlCommand *lastCommand;
@end

@implementation PESDKPlugin

@synthesize shouldSave;

+ (void)initialize {
    // Initialize the plugin and prepare the PESDK
    if (self == [PESDKPlugin self]) {
        [PESDK unlockWithLicenseAt:[[NSBundle mainBundle] URLForResource:@"ios_license" withExtension:nil]];
    }
}

#pragma mark - Cordova


/**
 Sends a result back to Cordova.

 @param result
 */
- (void)finishCommandWithResult:(CDVPluginResult *)result {
    if (self.lastCommand != nil) {
        [self.commandDelegate sendPluginResult:result callbackId:self.lastCommand.callbackId];
        self.lastCommand = nil;
    }
}

#pragma mark - Public API

/**
 Presents a CameraViewController, passes the taken/selected
 image to the PhotoEditorViewController and saves the edited
 image to the iOS photo library upon save.
 
 The given command is finished with different results, depending
 on the actions taken by the user:
 - Cancelling the editor results in no result.
 - Saving an edited image results in an OK result with the images
 filepath given as parameter.
 - Any errors lead to a corresponding result
 
 See the `PESDKPhotoEditViewControllerDelegate` methods for
 more details.
 
 @param command The command to be finished with any results.
 */
- (void)present:(CDVInvokedUrlCommand *)command {
    if (self.lastCommand == nil) {
        self.lastCommand = command;

                // Parse arguments and extract filepath
        NSDictionary *options = command.arguments[0];
        NSString *filepath = options[@"path"];
        NSString *donePath = options[@"doneButton"];
        NSString *categoryImage = options[@"categoryImage"];
        NSString *categoryTitle = options[@"categoryTitle"];
        self.shouldSave = [options[@"shouldSave"] boolValue];
        NSLog(@"Bool value: %d", shouldSave);
        
        NSArray *customStickers = options[@"stickers"];
        
        NSMutableArray<PESDKStickerCategory *> *categories = [[NSMutableArray alloc] init];
        NSMutableArray<PESDKSticker *> *stickers = [[NSMutableArray alloc] init];
        
        if (customStickers && categoryImage) {
            for (NSDictionary *stickerItem in customStickers) {
                NSURL *url = [NSURL URLWithString:[stickerItem objectForKey:@"image_url"]];
                NSURL *urlThumb = [NSURL URLWithString:[stickerItem objectForKey:@"image_thumb_url"]];
                [stickers addObject:[[PESDKSticker alloc] initWithImageURL:url thumbnailURL:urlThumb identifier:url.path]];
            }
            
            if (!categoryTitle) {
                categoryTitle = @"Main";
            }
            
            NSURL *categoryUrl = [NSURL URLWithString:categoryImage];
            
            [categories addObject:[[PESDKStickerCategory alloc] initWithTitle:categoryTitle imageURL:categoryUrl stickers:stickers]];
            
            PESDKStickerCategory.all = categories;
        }
        
        if (donePath) {
            [PESDK setBundleImageBlock:^UIImage * _Nullable(NSString * _Nonnull name) {
                if ([name isEqualToString:@"imgly_icon_save"]) {
                    return [UIImage imageNamed:donePath];
                }
                return nil;
            }];
        }
        
    
        PESDKConfiguration *configuration = [[PESDKConfiguration alloc] initWithBuilder:^(PESDKConfigurationBuilder * _Nonnull builder) {
            PESDKCropAspect *squareCrop = [[PESDKCropAspect alloc] initWithWidth:1.0 height:1.0 localizedName:@"Square"];
            
            if (customStickers && categoryImage) {
                [builder configureStickerToolController:^(PESDKStickerToolControllerOptionsBuilder *tool) {
                    tool.defaultStickerCategoryIndex = 0;
                }];
            }
            
            [builder transformToolControllerOptions:^(PESDKTransformToolControllerOptionsBuilder *tool) {
                tool.allowFreeCrop = false;
                tool.allowedCropRatios = [NSArray arrayWithObject:squareCrop];
            }];
        }];

        if (filepath) {
            NSError *dataCreationError;
            NSData *imageData = [NSData dataWithContentsOfFile:filepath options:0 error:&dataCreationError];
            
            // Open PESDK
            if (imageData && !dataCreationError) {
                PESDKPhotoEditViewController *photoEditViewController = [[PESDKPhotoEditViewController alloc] initWithData:imageData configuration:configuration];
                photoEditViewController.delegate = self;

                dispatch_async(dispatch_get_main_queue(), ^{
                    [self.viewController presentViewController:photoEditViewController animated:YES completion:nil];
                });
            } else if (dataCreationError) {
                NSLog(@"Failed to open given path: %@", dataCreationError);
            }
        } else {
            PESDKCameraViewController *cameraViewController = [[PESDKCameraViewController alloc] initWithConfiguration:configuration];
            [cameraViewController setCompletionBlock:^(UIImage * _Nullable image, NSURL * _Nullable url) {
                PESDKPhotoEditViewController *photoEditViewController = [[PESDKPhotoEditViewController alloc] initWithPhoto:image configuration:configuration];
                photoEditViewController.delegate = self;
                [self.viewController dismissViewControllerAnimated:YES completion:^{
                    [self.viewController presentViewController:photoEditViewController animated:YES completion:nil];
                }];
            }];
            
            dispatch_async(dispatch_get_main_queue(), ^{
                [self.viewController presentViewController:cameraViewController animated:YES completion:nil];
            });
        }
    }
}


/**
 Closes all PESDK view controllers and sends a result
 back to Cordova.

 @param result The result to be sent.
 */
- (void)closeControllerWithResult:(CDVPluginResult *)result {
    [self.viewController dismissViewControllerAnimated:YES completion:^{
        [self finishCommandWithResult:result];
    }];
}

#pragma mark - Filesystem Workaround

// See https://issues.apache.org/jira/browse/CB-8032 for context about
// these methods.

- (NSURL *)urlTransform:(NSURL *)url {
    NSURL *urlToTransform = url;

    // For backwards compatibility - we check if this property is there
    SEL sel = NSSelectorFromString(@"urlTransformer");
    if ([self.commandDelegate respondsToSelector:sel]) {
        // Grab the block from the commandDelegate
        NSURL * (^urlTransformer)(NSURL *) = ((id(*)(id, SEL))objc_msgSend)(self.commandDelegate, sel);
        // If block is not null, we call it
        if (urlTransformer) {
            urlToTransform = urlTransformer(url);
        }
    }

    return urlToTransform;
}

- (NSString *)tempFilePath:(NSString *)extension {
    NSString *docsPath = [NSTemporaryDirectory() stringByStandardizingPath];
    NSFileManager *fileMgr = [[NSFileManager alloc] init]; // recommended by Apple (vs [NSFileManager
    // defaultManager]) to be threadsafe
    NSString *filePath;

    // Generate unique file name
    int i = 1;
    do {
        filePath = [NSString stringWithFormat:@"%@/%@%d.%@", docsPath, @"PESDKPlugin_", i++, extension];
    } while ([fileMgr fileExistsAtPath:filePath]);

    return filePath;
}

- (NSURL *)copyTempImageData:(NSData *)data withUTI:(NSString *)uti {
    NSString *filePath = [self tempFilePath:uti];
    NSError *err = nil;
    NSURL *url = nil;

    // Save file
    if ([data writeToFile:filePath options:NSAtomicWrite error:&err]) {
        url = [self urlTransform:[NSURL fileURLWithPath:filePath]];
    }
    return url;
}

#pragma mark - PESDKPhotoEditViewControllerDelegate

// The PhotoEditViewController did save an image.
- (void)photoEditViewController:(PESDKPhotoEditViewController *)photoEditViewController didSaveImage:(UIImage *)image imageAsData:(NSData *)data {
    if (image) {
        if(!photoEditViewController.hasChanges) {
            NSLog(@"Images are the same");
            [self returnBasicImage:image];
        } else {
            [self saveImageToPhotoLibrary:image];
        }
    } else {
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT];
        [self closeControllerWithResult:result];
    }
}

// The PhotoEditViewController was cancelled.
- (void)photoEditViewControllerDidCancel:(PESDKPhotoEditViewController *)photoEditViewController {
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_NO_RESULT];
    [self closeControllerWithResult:result];
}

// The PhotoEditViewController could not create an image.
- (void)photoEditViewControllerDidFailToGeneratePhoto:(PESDKPhotoEditViewController *)photoEditViewController {
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Image editing failed."];
    [self closeControllerWithResult:result];
}


#pragma mark - Result Handling
/**
 Process the image handling
**/

- (NSData*)processImage:(UIImage*)image
{
    NSData* data = nil;
    data = UIImageJPEGRepresentation(image, 90 / 100.0f);
    return data;
}

/**

Simple return of the original image
 **/
- (void)returnBasicImage:(UIImage *)image {
     [self.commandDelegate runInBackground:^{
         CDVPluginResult *resultAsync;
         NSString *extension =  @"jpg";
         NSData* imageData = [self processImage:image];
         NSURL *url = [self copyTempImageData:imageData withUTI:extension];
         NSString *urlString = [url absoluteString];
         NSDictionary *payload = [NSDictionary dictionaryWithObjectsAndKeys:urlString, @"url", extension, @"uti", nil];
         resultAsync = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                     messageAsDictionary:payload];

         dispatch_async(dispatch_get_main_queue(), ^{
             [self closeControllerWithResult:resultAsync];
         });
     }];
}
/**
 Saves an image to the iOS Photo Library and sends
 the corresponding results to Cordova.

 @param image The image to be saved.
 */
- (void)saveImageToPhotoLibrary:(UIImage *)image {
    [self.commandDelegate runInBackground:^{

        NSLog(@"Bool Save value: %d", self.shouldSave);

        if (!self.shouldSave) {
            CDVPluginResult *resultAsync;
            NSString *extension =  @"jpg";
            NSData* imageData = [self processImage:image];
            NSURL *url = [self copyTempImageData:imageData withUTI:extension];
            NSString *urlString = [url absoluteString];
            NSDictionary *payload = [NSDictionary dictionaryWithObjectsAndKeys:urlString, @"url", extension, @"uti", nil];
            resultAsync = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                        messageAsDictionary:payload];

            dispatch_async(dispatch_get_main_queue(), ^{
                [self closeControllerWithResult:resultAsync];
            });
        } else {
            __block PHObjectPlaceholder *assetPlaceholder = nil;

            // Apple did a great job at making this API convoluted as fuck.
            PHPhotoLibrary *photos = [PHPhotoLibrary sharedPhotoLibrary];
            [photos performChanges:^{
                // Request creating an asset from the image.
                PHAssetChangeRequest *createAssetRequest = [PHAssetChangeRequest creationRequestForAssetFromImage:image];
                // Get a placeholder for the new asset.
                assetPlaceholder = [createAssetRequest placeholderForCreatedAsset];
            } completionHandler:^(BOOL success, NSError *error) {
                CDVPluginResult *result;
                if (success) {
                    PHAsset *asset = [PHAsset fetchAssetsWithLocalIdentifiers:@[ assetPlaceholder.localIdentifier ] options: nil].firstObject;
                    if (asset != nil) {
                        // Fetch high quality image and save in folder
                        PHImageRequestOptions *operation = [[PHImageRequestOptions alloc] init];
                        operation.deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat;
                        [[PHImageManager defaultManager] requestImageDataForAsset:asset
                                                                          options:operation
                                                                    resultHandler:^(NSData *imageData, NSString *dataUTI, UIImageOrientation orientation, NSDictionary *info) {
                                                                        CDVPluginResult *resultAsync;
                                                                        NSError *error = [info objectForKey:PHImageErrorKey];
                                                                        if (error != nil) {
                                                                            resultAsync = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:[error localizedDescription]];
                                                                        } else {
                                                                            NSURL *url = [self copyTempImageData:imageData withUTI:dataUTI];
                                                                            NSString *urlString = [url absoluteString];
                                                                            NSDictionary *payload = [NSDictionary dictionaryWithObjectsAndKeys:urlString, @"url", dataUTI, @"uti", nil];
                                                                            resultAsync = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                                                                        messageAsDictionary:payload];
                                                                        }

                                                                        dispatch_async(dispatch_get_main_queue(), ^{
                                                                            [self closeControllerWithResult:resultAsync];
                                                                        });
                                                                    }];
                        return;
                    } else {
                        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                   messageAsString:@"Failed to load photo asset."];
                    }
                } else {
                    result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                               messageAsString:[error localizedDescription]];
                }

                dispatch_async(dispatch_get_main_queue(), ^{
                    [self closeControllerWithResult:result];
                });
            }];
        }



    }];
}

@end
