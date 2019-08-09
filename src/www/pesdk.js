var PESDK = {
  present: function(success, failure, options) {
    console.log("Present:");
    console.log(options);
    cordova.exec(success, failure, "PESDK", "present", [options]);
  },
    deleteMedia: function(success, failure, options) {
      console.log('delete:');
      console.log(options);
      cordova.exec(success, failure, 'PESDK', 'delete', [options]);
    }
};
module.exports = PESDK;
