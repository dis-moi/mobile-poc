import { FloatingModule } from '../nativeModules/get';
import { DeviceEventEmitter } from 'react-native';
import { Linking } from 'react-native';

import { formatDate } from '../libraries';

import 'moment/min/locales';

function getNoticeIds(matchingContexts, eventMessageFromChromeURL) {
  return matchingContexts
    .map((res) => {
      const addWWWForBuildingURL = `www.${eventMessageFromChromeURL}`;

      if (res.xpath) {
        return;
      }

      if (addWWWForBuildingURL.match(new RegExp(res.urlRegex, 'g'))) {
        return res.noticeId;
      }
    })
    .filter(Boolean);
}

let matchingContexts = [];

function callActionListeners() {
  DeviceEventEmitter.addListener('floating-dismoi-bubble-press', (e) => {
    return FloatingModule.showFloatingDisMoiMessage(0, 1500).then(() => {
      // What to do when user press on the bubble
      console.log('Bubble press');
    });
  });
  DeviceEventEmitter.addListener('floating-dismoi-message-press', (e) => {
    // What to do when user press on the message
    return FloatingModule.hideFloatingDisMoiMessage().then(() => {});
  });

  DeviceEventEmitter.addListener('floating-dismoi-bubble-remove', (e) => {
    // What to do when user press on the message
    console.log('FLOATING DISMOI BUBBLE REMOVE');
  });

  DeviceEventEmitter.addListener('URL_CLICK_LINK', (event) => {
    FloatingModule.initialize().then(() => {
      FloatingModule.hideFloatingDisMoiBubble().then(() =>
        FloatingModule.hideFloatingDisMoiMessage()
      );
      Linking.openURL(event);
    });
  });
}

let url = '';

async function callMatchingContext() {
  console.log('_________________CALL MATHING CONTEXT____________________');

  matchingContexts = await fetch(
    'https://notices.bulles.fr/api/v3/matching-contexts'
  ).then((response) => {
    console.log(
      '_________________END CALL MATHING CONTEXT____________________'
    );

    return response.json();
  });
}

let urlList = [];

const HeadlessTask = async (taskData) => {
  console.log('TASK DATA');
  console.log(taskData);

  if (matchingContexts.length === 0) {
    callActionListeners();
    FloatingModule.initialize();
    await callMatchingContext();
  }

  if (taskData.hide === 'true') {
    FloatingModule.hideFloatingDisMoiBubble().then(() =>
      FloatingModule.hideFloatingDisMoiMessage()
    );

    return;
  }

  const eventMessageFromChromeURL = taskData.url;

  if (eventMessageFromChromeURL) {
    if (
      eventMessageFromChromeURL.indexOf(' ') >= 0 ||
      taskData.eventType === 'TYPE_VIEW_TEXT_CHANGED'
    ) {
      if (eventMessageFromChromeURL.indexOf(' ') >= 0) {
        urlList.push('no url');
      }

      url = '';
      FloatingModule.hideFloatingDisMoiBubble().then(() =>
        FloatingModule.hideFloatingDisMoiMessage()
      );
      return;
    }

    if (taskData.eventText === '') {
      const noticeIds = getNoticeIds(
        matchingContexts,
        eventMessageFromChromeURL
      );

      let notices = await Promise.all(
        noticeIds.map((noticeId) =>
          fetch(
            `https://notices.bulles.fr/api/v3/notices/${noticeId}`
          ).then((response) => response.json())
        )
      );
      if (notices.length > 0) {
        const numberOfNotice = notices.length;
        const noticesToShow = notices.map((res) => {
          const formattedDate = formatDate(res);

          res.modified = formattedDate;

          return res;
        });

        if (url !== eventMessageFromChromeURL) {
          if (
            taskData.className === 'android.widget.FrameLayout' &&
            urlList.includes('no url')
          ) {
            urlList = [];
            return;
          }

          url = eventMessageFromChromeURL;
          FloatingModule.showFloatingDisMoiBubble(
            10,
            1500,
            numberOfNotice,
            noticesToShow,
            eventMessageFromChromeURL
          ).then(() => {});
        }
      }
    }
  }
};

export default HeadlessTask;
