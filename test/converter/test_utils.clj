(ns converter.test-utils
  (:require
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.spec.test.alpha :as stest]
   [clojure.test :refer [deftest is testing]]
   [clojure.test.check]
   [clojure.test.check.generators]
   [clojure.test.check.properties]
   [converter.config :as config]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.str :as str]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [converter.xml :as xml]
   [spec-tools.core :as st]))

(s/fdef test-fn-violates
  :args (s/cat :x int?)
  :ret string?)

(defn test-fn-violates
  "A function that violates its return spec"
  [_] 1)

(s/fdef test-fn-conforms
  :args (s/cat :x int?)
  :ret string?)

(defn test-fn-conforms
  "A function that conforms its return spec"
  [_] "1")

(defn check-spec-results
  "Returns true if all spec test results passed"
  [results]
  (doseq [result results]
    (prn (assoc (:clojure.spec.test.check/ret result) :test-sym (str (:sym result)))))
  (every? #(-> % :clojure.spec.test.check/ret :pass?) results))

(deftest test-check-spec-results
  (let [fn-conforms-results (stest/check `test-fn-conforms {:clojure.spec.test.check/opts {:num-tests 5}})
        fn-violates-results (stest/check `test-fn-violates {:clojure.spec.test.check/opts {:num-tests 5}})]
    (is (true? (check-spec-results fn-conforms-results)))
    (is (false? (check-spec-results fn-violates-results)))))

(defmacro deftest-check
  "Macro to generate a deftest that runs clojure.spec.test.alpha/check on sym and then checks all results passed"
  ([name sym] `(deftest-check ~name ~sym 100))
  ([name sym num-tests]
   `(deftest ~name
      (let [results# (stest/check ~sym {:clojure.spec.test.check/opts {:num-tests ~num-tests}})]
        (is (check-spec-results results#))))))

(defn tmpdir
  []
  (let [dir (System/getProperty "java.io.tmpdir")]
    (io/file dir (gen/generate (str/not-blank-string-gen)))))

(defn rmdir
  [dir]
  (let [contents (file-seq dir)
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