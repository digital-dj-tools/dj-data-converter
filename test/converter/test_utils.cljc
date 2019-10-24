(ns converter.test-utils
  (:require
   #?(:clj [clojure.java.io :as io] :cljs [cljs-node-io.core :as io :refer [file-seq slurp spit]])
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.test.check]
   [clojure.test.check.generators]
   [clojure.test.check.properties]
   [converter.app :as app]
   [converter.config :as config]
   [converter.time :as time]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [converter.str :as str]
   [converter.xml :as xml]
   #?(:cljs [os :as os])
   [spec-tools.core :as st]
   [tick.alpha.api :as tick]))

(defn tmpdir
  []
  (let [dir #?(:clj (System/getProperty "java.io.tmpdir")
               :cljs (.tmpdir os))]
    (io/file dir (gen/generate (str/not-blank-string-gen)))))

(defn rmdir
  [dir]
  (let [contents #?(:clj (file-seq dir)
                    :cljs (file-seq (str dir)))
        files (filter #(.isFile (io/file %)) contents)]
    (doseq [file files]
      (.delete (io/file file)))
    (.delete (io/file dir))))

; TODO move to universal.core ns
(defn- library-items-map
  [library item-map-fn]
  (if (::u/collection library)
    (update library ::u/collection #(map item-map-fn %))))

; TODO move to universal.core ns
(defn- library-items-filter
  [library item-filter-fn]
  (if (::u/collection library)
    (update library ::u/collection #(filter item-filter-fn %))))

(defn- item-markers-remove-non-indexed-markers-with-matching-indexed-marker
  [item]
  (if (::u/markers item)
    (update item ::u/markers #(concat (um/indexed-markers %) (um/non-indexed-markers-without-matching-indexed-marker %)))
    item))

(defn- item-tempos-dissoc-bpm-metro-battito
  [item]
  (if (::u/tempos item)
    (update item ::u/tempos (fn [tempos] (mapv #(dissoc % ::ut/bpm ::ut/metro ::ut/battito) tempos)))
    item))

(defn library-equiv-traktor
  "Returns a library that is expected to be equivalent with the given library, after it has been converted to Traktor data and back again"
  [library]
  ; TODO for the first tempo of each item, assert bpm's are equal (in addition to inizio being equal)
  ((comp
    #(library-items-map % u/sorted-tempos)
    #(library-items-map % item-tempos-dissoc-bpm-metro-battito)
    #(library-items-map % u/sorted-markers)
    #(library-items-map % item-markers-remove-non-indexed-markers-with-matching-indexed-marker))
   library))

(defn- marker-type-supported?
  [marker-type]
  (contains? #{::um/type-cue ::um/type-loop} marker-type))

(defn- item-markers-unsupported-type->cue-type
  [item]
  (if (::u/markers item)
    (update item ::u/markers (fn [markers] (mapv #(if (marker-type-supported? %)
                                                    %
                                                    (assoc % ::um/type ::um/type-cue)) markers)))
    item))

(defn library-equiv-rekordbox
  "Returns a library that is expected to be equivalent with the given library, after it has been converted to Rekordbox data and back again"
  [library]
  ((comp 
    #(library-items-map % item-markers-unsupported-type->cue-type)
    #(library-items-filter % u/item-contains-total-time?)) 
   library))

(def config
  (gen/generate (s/gen ::config/config)))

(defn traktor-round-trip
  [config library]
  (as-> library $
    (st/encode (t/nml-spec config) $ t/xml-transformer)
    (xml/encode $)
    (xml/decode $)
    (spec/decode! (t/nml-spec config) $ t/string-transformer)
    (spec/decode! t/library-spec $ t/xml-transformer)))

(defn rekordbox-round-trip
  [config library]
  (as-> library $
    (st/encode (r/dj-playlists-spec config) $ r/xml-transformer)
    (xml/encode $)
    (xml/decode $)
    (spec/decode! (r/dj-playlists-spec config) $ r/string-transformer)
    (spec/decode! r/library-spec $ r/xml-transformer)))

(defn xml-from-file
  [input-file]
  (as-> input-file $
    (slurp $)
    (xml/decode $)))

(defn library-from-rekordbox-file
  [input-file]
  (as-> input-file $
    (slurp $)
    (xml/decode $)
    (spec/decode! (r/dj-playlists-spec config) $ r/string-transformer)
    (spec/decode! r/library-spec $ r/xml-transformer)))

; we do it this way, because gen/sample doesn't make lazy seqs
(defn- collection
  [item-spec]
  (lazy-seq
   (cons (gen/generate (s/gen item-spec)) (collection item-spec))))

(defn library
  [item-spec n]
  (as-> u/library-spec $
    (s/gen $)
    (gen/generate $)
    ; TODO make a library spec that doesn't have a collection,
    ; rather than generating a collection and then throwing it away/replacing it as done here
    (assoc $ ::u/collection (take n (collection item-spec)))))

(defn library-from-rekordbox
  [n]
  (library u/item-from-rekordbox-spec n))