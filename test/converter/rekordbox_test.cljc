(ns converter.rekordbox-test
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.test.check]
   [clojure.test.check.clojure-test :refer [defspec] #?@(:cljs [:include-macros true])]
   [clojure.test.check.properties :as tcp #?@(:cljs [:include-macros true])]
   [converter.rekordbox.core :as r]
   [converter.rekordbox.position-mark :as rp]
   [converter.rekordbox.tempo :as rt]
   [plumula.mimolette.alpha :refer [defspec-test]]))

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
  `t/track->item
  {:opts {:num-tests 100}})

(defspec-test
  item->track
  `r/item->track
  {:opts {:num-tests 100}})

(defspec-test
  library->dj-playlists
  `r/library->dj-playlists
  {:opts {:num-tests 10}})
