(ns converter.app
  (:require
   [clojure.data.zip.xml :as zx]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [clojure.zip :as zip]
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]
   [converter.xml :as xml]
   [spec-tools.core :as st]))

(defprotocol TraktorRekordboxConverter
  (input-spec [this])
  (input-collection [this input-data])
  (library-spec [this])
  (output-spec [this config])
  (output-collection [this output-data]))

(def traktor->rekordbox
  (reify
    TraktorRekordboxConverter
    (input-spec
      [this]
      (t/nml-spec))
    (input-collection
      [this input-data]
      (zx/xml-> (zip/xml-zip input-data) :COLLECTION :ENTRY))
    (library-spec
      [this]
      t/library-spec)
    (output-spec
      [this progress]
      (r/dj-playlists-spec progress))
    (output-collection
      [this output-data]
      (zx/xml-> (zip/xml-zip output-data) :COLLECTION :TRACK))))

(defn doto-prn
  [obj f]
  (prn (f obj)))

(s/fdef convert
  :args (s/cat :config #{{:converter traktor->rekordbox}}
               :xml (spec/value-encoded-spec (t/nml-spec) spec/string-transformer))
  :ret (spec/value-encoded-spec r/dj-playlists-spec spec/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert
  [config xml]
  (let [input-spec (input-spec (:converter config))
        library-spec (library-spec (:converter config))
        output-spec (output-spec (:converter config) (:progress config))]
    (as-> xml $
      (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) (partial input-collection (:converter config)))))
      (spec/decode! input-spec $ spec/string-transformer)
      (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) (partial input-collection (:converter config)))))
      (spec/decode! library-spec $ spec/xml-transformer)
      (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) ::u/collection)))
      (st/encode output-spec $ spec/xml-transformer)
      (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) (partial output-collection (:converter config))))))))