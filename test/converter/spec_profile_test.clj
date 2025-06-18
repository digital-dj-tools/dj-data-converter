(ns converter.spec-profile-test 
  (:require
   [clojure.test :refer [deftest]]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.test-utils :as test]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]
   [spec-tools.core :as st]
   [taoensso.tufte :refer [p profile]]))

;; Isolate poor performance of st/encode with t/nml-spec
(deftest ^:profile traktor-encode
  (let [item-spec (spec/such-that-spec u/item-from-traktor-spec
                                       u/item-contains-total-time?
                                       100)]
    (profile {:id "traktor-encode"}
             (as-> (p ::generate-library (test/library item-spec 100)) $
               (p ::encode (st/encode (t/nml-spec test/config) $ t/xml-transformer))))))

;; Comparatively good performance of st/encode with r/dj-playlists-spec
(deftest ^:profile rekordbox-encode
  (let [item-spec (spec/such-that-spec u/item-from-rekordbox-spec
                                       u/item-contains-total-time?
                                       100)]
    (profile {:id "rekordbox-encode"}
             (as-> (p ::generate-library (test/library item-spec 100)) $
               (p ::encode (st/encode (r/dj-playlists-spec test/config) $ r/xml-transformer))))))