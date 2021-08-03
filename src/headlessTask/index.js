import { Background, FloatingModule } from '../nativeModules/get';
import { DeviceEventEmitter } from 'react-native';
import { Linking } from 'react-native';

import { formatDate } from '../libraries';

import SharedPreferences from 'react-native-shared-preferences';

import 'moment/min/locales';

let _notices = [];

async function getNoticeIds(eventMessageFromChromeURL, matchingContexts) {
  const noticeIds = [];
  for (const matchingContext of matchingContexts) {
    const addWWWForBuildingURL = `www.${eventMessageFromChromeURL}`;

    if (addWWWForBuildingURL.match(new RegExp(matchingContext.urlRegex, 'g'))) {
      if (matchingContext.xpath) {
        continue;
      }
      noticeIds.push(matchingContext.noticeId);
    }
  }

  return noticeIds;
}

function callActionListeners() {
  DeviceEventEmitter.addListener('floating-dismoi-bubble-press', (e) => {
    FloatingModule.showFloatingDisMoiMessage(
      _notices,
      1500,
      _notices.length
    ).then(() => {
      // What to do when user press on the bubble
    });
  });

  DeviceEventEmitter.addListener('URL_CLICK_LINK', (event) => {
    FloatingModule.hideFloatingDisMoiBubble().then(() =>
      FloatingModule.hideFloatingDisMoiMessage()
    );
    Linking.openURL(event);
  });

  DeviceEventEmitter.addListener('DELETE_NOTICE', (event) => {
    const contributorName = _notices[parseInt(event)].contributor.name;

    const noticeId = _notices[parseInt(event)].id;
    SharedPreferences.getItem(contributorName, function (value) {
      const json = JSON.parse(value);
      json.noticeDeleted = noticeId;

      const stringifyJson = JSON.stringify(json);

      SharedPreferences.setItem(contributorName, stringifyJson);

      const foundIn = [parseInt(event)];
      var res = _notices.filter(function (eachElem, index) {
        return foundIn.indexOf(index) === -1;
      });

      if (_notices && _notices.length === 1) {
        FloatingModule.hideFloatingDisMoiMessage();
        return;
      }

      FloatingModule.showFloatingDisMoiMessage(res, 1500, res.length).then(
        () => {
          // What to do when user press on the message
          _notices = res;
        }
      );
    });
  });
}

let matchingContextFetchApi =
  'https://notices.bulles.fr/api/v3/matching-contexts?';

async function callMatchingContext(savedUrlMatchingContext) {
  console.log('_________________CALL MATHING CONTEXT____________________');

  const response = await fetch(
    matchingContextFetchApi + savedUrlMatchingContext
  );

  console.log('_________________END CALL MATHING CONTEXT____________________');

  return response.json();
}

function getNoticeIdsThatAreNotDeleted(contributors, noticesToShow) {
  const noticesIdToDelete = contributors
    .map((contributor) => {
      if (contributor?.noticeDeleted) {
        return contributor.noticeDeleted;
      }
    })
    .filter(Boolean);

  const noticesIdFromNoticesToShow = noticesToShow
    .map((notice) => {
      return notice.id;
    })
    .filter(Boolean);

  return noticesIdFromNoticesToShow.filter(
    (e) => !noticesIdToDelete.find((a) => e === a)
  );
}

let i = 0;
let eventTimes = [];
let packageName = '';

const HeadlessTask = async (taskData) => {
  packageName = taskData.packageName;

  if (taskData.eventTime) {
    eventTimes.push(parseInt(taskData.eventTime));
  }

  if (taskData.url) {
    if (eventTimes.length === 2) {
      const eventTime = eventTimes[1] - eventTimes[0];
      if (eventTime < 1000) {
        eventTimes = [];
        return;
      }
    }
    if (i === 0) {
      callActionListeners();
      i++;
    }

    if (taskData.hide === 'true') {
      FloatingModule.hideFloatingDisMoiBubble().then(() => {
        FloatingModule.hideFloatingDisMoiMessage().then(() => {});
      });
      return;
    }
    SharedPreferences.getItem('url', async function (savedUrlMatchingContext) {
      const matchingContexts = await callMatchingContext(
        savedUrlMatchingContext
      );

      const eventMessageFromChromeURL = taskData.url;
      if (eventMessageFromChromeURL) {
        let noticeIds = await getNoticeIds(
          eventMessageFromChromeURL,
          matchingContexts
        );
        const uniqueIds = [...new Set(noticeIds)];
        let notices = await Promise.all(
          uniqueIds.map((noticeId) =>
            fetch(
              `https://notices.bulles.fr/api/v3/notices/${noticeId}`
            ).then((response) => response.json())
          )
        );
        if (notices.length > 0) {
          const noticesToShow = notices.map((result) => {
            const formattedDate = formatDate(result);
            result.modified = formattedDate;
            return result;
          });
          SharedPreferences.getAll(function (values) {
            const contributors = [
              ...new Set(
                values
                  .map((result) => {
                    if (result[0] !== 'url') {
                      return JSON.parse(result[1]);
                    }
                  })
                  .filter(Boolean)
              ),
            ];
            const noticeIdNotDeleted = getNoticeIdsThatAreNotDeleted(
              contributors,
              noticesToShow
            );
            if (noticeIdNotDeleted.length > 0) {
              _notices = noticeIdNotDeleted.map((id) => {
                return noticesToShow.find(
                  (noticeToShow) => noticeToShow.id === id
                );
              });
              if (packageName === 'com.android.chrome') {
                FloatingModule.initializeBubblesManager().then(() => {
                  FloatingModule.showFloatingDisMoiBubble(
                    10,
                    1500,
                    notices.length,
                    eventMessageFromChromeURL
                  ).then(() => {
                    noticeIds = [];
                    eventTimes = [];
                  });
                });
              }
            }
          });
        }
      }
    });
  }
};

export default HeadlessTask;
