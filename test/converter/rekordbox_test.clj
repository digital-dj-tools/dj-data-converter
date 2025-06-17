(ns converter.rekordbox-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :refer [is]]
   [clojure.test.check]
   [clojure.test.check.clojure-test :refer [defspec]]
   [clojure.test.check.properties :as tcp]
   [converter.config :as config]
   [converter.spec :as spec]
   [converter.rekordbox.core :as r]
   [converter.rekordbox.position-mark :as rp]
   [converter.rekordbox.tempo :as rt]
   [converter.test-utils :as test :refer [deftest-check]]
   [converter.universal.core :as u]
   [spec-tools.core :as st]))

(deftest-check test-tempo->item-tempo `rt/tempo->item-tempo 100)

(deftest-check test-item-tempo->tempo `rt/item-tempo->tempo 100)

(deftest-check test-position-mark->marker `rp/position-mark->marker 100)

(deftest-check test-marker->position-mark `rp/marker->position-mark 100)

(deftest-check test-track->item `r/track->item 100)

(deftest-check test-item->track `r/item->track 100)

(deftest-check test-dj-playlists->library `r/dj-playlists->library 10)

(deftest-check test-library->dj-playlists `r/library->dj-playlists 10)

(defspec position-mark-spec-encode-decode-equality
  100
  (tcp/for-all [position-mark (s/gen rp/position-mark-spec)]
               (as-> position-mark $
                 (st/encode rp/position-mark-spec $ r/string-transformer)
                 (st/decode rp/position-mark-spec $ r/string-transformer)
                 (is (= position-mark $)))))

(defspec track-spec-encode-decode-equality
  10
  (tcp/for-all [track (s/gen r/track-spec)]
               (as-> track $
                 (st/encode r/track-spec $ r/string-transformer)
                 (spec/decode! r/track-spec $ r/string-transformer)
                 (is (= track $)))))

(defspec dj-playlists-spec-encode-decode-equality
  10
  (tcp/for-all [dj-playlists (s/gen (r/dj-playlists-spec test/config))]
               (as-> dj-playlists $
                 (st/encode (r/dj-playlists-spec test/config) $ r/string-transformer)
                 (spec/decode! (r/dj-playlists-spec test/config) $ r/string-transformer)
                 (is (= dj-playlists $)))))

(defspec library-spec-round-trip-library-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)
                config (s/gen ::config/config)]
               (as-> library $
                 (test/rekordbox-round-trip config $)
                 (is (= (test/library-equiv-rekordbox library)
                        (test/library-equiv-rekordbox $))))))

(defspec library-spec-round-trip-xml-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)
                config (s/gen ::config/config)]
               (as-> library $
                 (test/rekordbox-round-trip config $)
                 (test/library-equiv-rekordbox $)
                 (st/encode (r/dj-playlists-spec config) $ r/xml-transformer)
                 (is (= (st/encode (r/dj-playlists-spec config) (test/library-equiv-rekordbox library) r/xml-transformer)
                        $)))))