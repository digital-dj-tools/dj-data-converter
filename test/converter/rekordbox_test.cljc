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
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.xml :as xml]
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
  dj-playlists->library
  `r/dj-playlists->library
  {:opts {:num-tests 10}})

(defspec-test
  library->dj-playlists
  `r/library->dj-playlists
  {:opts {:num-tests 10}})

(defspec position-mark-spec-encode-decode-equality
  100
  (tcp/for-all [position-mark (s/gen rp/position-mark-hot-cue-or-memory-cue-spec)]
               (as-> position-mark $
                 (st/encode rp/position-mark-hot-cue-or-memory-cue-spec $ st/string-transformer)
                 (st/decode rp/position-mark-hot-cue-or-memory-cue-spec $ st/string-transformer)
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

(defn library-items-filter-contains-total-time
  [library]
  (update
   library
   ::u/collection
   (partial filter u/item-contains-total-time?)))

(defn item-markers-unsupported-type->cue-type
  [item]
  (if (::u/markers item)
    (update item ::u/markers (fn [markers] (mapv #(if (rp/marker-type-supported? %) % (assoc % ::um/type ::um/type-cue)) markers)))
    item))

(defn library-items-markers-unsupported-type->cue-type
  [library]
  (update
   library
   ::u/collection
   (fn [items] (map #(item-markers-unsupported-type->cue-type %) items))))

(defspec library-spec-round-trip-library-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)]
               (as-> library $
                 (st/encode (r/dj-playlists-spec) $ spec/xml-transformer)
                 (xml/encode $)
                 (xml/decode $)
                 (spec/decode! (r/dj-playlists-spec) $ spec/string-transformer)
                 (spec/decode! r/library-spec $ spec/xml-transformer)
                 (is (= ((comp library-items-markers-unsupported-type->cue-type library-items-filter-contains-total-time) library)
                        (library-items-markers-unsupported-type->cue-type $))))))

(defspec library-spec-round-trip-xml-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)]
               (as-> library $
                 (st/encode (r/dj-playlists-spec) $ spec/xml-transformer)
                 (xml/encode $)
                 (xml/decode $)
                 (spec/decode! (r/dj-playlists-spec) $ spec/string-transformer)
                 (spec/decode! r/library-spec $ spec/xml-transformer)
                 (library-items-markers-unsupported-type->cue-type $) ; marker types unsupported by rekordbox lost in conversion
                 (st/encode (r/dj-playlists-spec) $ spec/xml-transformer)
                 (let [library-equiv ((comp library-items-markers-unsupported-type->cue-type
                                            library-items-filter-contains-total-time) library)]
                   (is (= (st/encode (r/dj-playlists-spec) library-equiv spec/xml-transformer)
                          $))))))