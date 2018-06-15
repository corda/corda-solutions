
* BNOs will distribute a static list of current / deprecated cordapps via Corda Flows. Each cordapp can be distributed via different distribution mechanisms: flows, cdn and etc. Distribution mechanisms would be designed to be extendable and pluggable. The first implementation might include just 1 or 2 of them: flows and web for example. How-to-implement-an-own-download-adapter tutorial would be provided. CorDapps list might look like:

cordapps : {
  "current" : [
    {
      "cordapp" : "accenture:accenture-states:1.2",
      "distribution" : { // defines how the cordapp is distributed
        "type" : "flows"
      }
    }, {
      "cordapp" : "accenture:accenture-flows:1.0",
      "distribution" : {
        "type" : "web",
        "url" : "http://my-super-cdn"
      }
    }
  ],
  "deprecated" : ["accenture:accenture-states:0.9", ] // if a locally installed cordapp appears on the deprecation list, it should trigger a warning
}

* Each BN member would download the cordapp list when their node starts.
* BNO would notify all members every time the list changes, i.e. when a new update is available or some version of a cordapp gets deprecated. As the list is fairly small, the whole of it can be distributed along with each message.
* Every time the list changes, each member's responding flows would scan locally installed cordapps and would verify them against the deprecated list. If any of the locally installed cordapps happens to be deprecated, it should fire a notification to the node administrator (raise an alert on monitoring dashboard, send an email or something else)
* Similarly to *download adapters*, *notification adapters* would also be pluggable. We can include "send a notification via SMTP adapter" by default and also provide tutorials about how users can implement their own notification adapters.
* Node administrators would be able to configure behaviour of their applications. The configuration might look like:

{
  "distribution" : { // configures how to download updates
    "web" : {
      "downloadAdapter" : "com.my.adapters.MyCdnDownloadAdapter", // which adapter to use to download from web
      "configuration" : {
        "authentication" : {
          // custom authentication parameters here
        },
        // some other custom configuration parameters
      },
    },
    "flows" : {
      "downloadAdapter" : "com.my.adapters.DoNothingAdapter", // don't download updates automatically
      "configuration" : {
        // some custom configuration here
      },
    }
  },
  "notification" : {
    "onUpdate" : {
      "notificationAdapter" : "com.my.adapters.SendEmailViaSMTPAdapter",
      "configuration" : {
        // some custom configuration
      }
    },
    "onDeprecate" : {
      "notificationAdapter" : "com.my.adapters.RaiseIssueOnMonitoringDashboardAdapter",
      "configuration" : {
        // some custom configuration
      }
    }
  }
}

* Node administrators should follow standard DevOps procedures to install the updates, such as switch the node into flows draining mode, wait until all flows finish, copy the jars to cordapps folder and and etc.
* As installing cordapp update involves some manual work to be done by a node administrator, my proposal is to not to worry about automation of it for now.

Issues:

* BNO node might get overloaded if everyone would start downloading updates in the same time and hence wouldn't be able to perform their BAU tasks. Possible resolutions:
  - To distribute applications via CDN
  - To distribute updates via flows only inside the system maintenance window, when volume of transaction is envisaged to be low (i.e. weekends).
* CZ enforces max size constraint on messages which can be sent via flows. Large cordapps would have to be split into multiple chunks in order to be distributed via flows.
