(ns converter.rekordbox-test
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.test :refer [is] #?@(:cljs [:include-macros true])]
   [clojure.test.check]
   [clojure.test.check.clojure-test :refer [defspec] #?@(:cljs [:include-macros true])]
   [clojure.test.check.properties :as tcp #?@(:cljs [:include-macros true])]
   [converter.spec :as spec]
   [converter.rekordbox.core :as r]
   [converter.rekordbox.position-mark :as rp]
   [converter.rekordbox.tempo :as rt]
   [plumula.mimolette.alpha :refer [defspec-test]]
   [spec-tools.core :as st]))

(defspec-test
  tempo->item-tempo
  `rt/tempo->item-tempo
  {:opts {:num-tests 100}})

(defspec-test
  item-tempo->tempo
  `rt/item-tempo->tempo
  {:opts {:num-tests 100}})

(defspec-test
  position-mark->marker
  `rp/position-mark->marker
  {:opts {:num-tests 100}})

(defspec-test
  marker->position-mark
  `rp/marker->position-mark
  {:opts {:num-tests 100}})

(defspec-test
  track->item
  `r/track->item
  {:opts {:num-tests 100}})

(defspec-test
  item->track
  `r/item->track
  {:opts {:num-tests 100}})

(defspec-test
  library->dj-playlists
  `r/library->dj-playlists
  {:opts {:num-tests 10}})

(defspec position-mark-spec-encode-decode-equality
  100
  (tcp/for-all [position-mark (s/gen rp/position-mark-with-shadow-marks-spec)]
               (as-> position-mark $
                 (st/encode rp/position-mark-with-shadow-marks-spec $ st/string-transformer)
                 (st/decode rp/position-mark-with-shadow-marks-spec $ st/string-transformer)
                 (is (= position-mark $)))))

(defspec track-spec-encode-decode-equality
  10
  (tcp/for-all [track (s/gen r/track-spec)]
               (as-> track $
                 (st/encode r/track-spec $ st/string-transformer)
                 (spec/decode! r/track-spec $ st/string-transformer)
                 (is (= track $)))))

(defspec dj-playlists-spec-encode-decode-equality
  10
  (tcp/for-all [dj-playlists (s/gen (r/dj-playlists-spec))]
               (as-> dj-playlists $
                 (st/encode (r/dj-playlists-spec) $ st/string-transformer)
                 (spec/decode! (r/dj-playlists-spec) $ st/string-transformer)
                 (is (= dj-playlists $)))))