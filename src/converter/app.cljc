(ns converter.app
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.rekordbox.core :as r]
   [converter.traktor.core :as t]
   [converter.spec :as spec]
   [converter.xml :as xml]
   [spec-tools.core :as st]))

(defprotocol TraktorRekordboxConverter
  (input-spec [this])
  (library-spec [this])
  (output-spec [this config]))

(def traktor->rekordbox
  (reify
    TraktorRekordboxConverter
    (input-spec
      [this]
      (t/nml-spec))
    (library-spec
      [this]
      t/library-spec)
    (output-spec
      [this progress]
      (r/dj-playlists-spec progress))))

(defn doto-prn
  [obj f]
  (prn (f obj)))

(s/fdef convert-data
  :args (s/cat :xml (spec/value-encoded-spec (t/nml-spec) spec/string-transformer)
               :config #{{:converter traktor->rekordbox}})
  :ret (spec/value-encoded-spec r/dj-playlists-spec spec/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert-data
  [xml config]
  (let [input-spec (input-spec (:converter config))
        library-spec (library-spec (:converter config))
        output-spec (output-spec (:converter config) (:progress config))]
    (as-> xml $
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) :content first next next :content)))
      (spec/decode! input-spec $ spec/string-transformer)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) :content first next next :content)))
      (spec/decode! library-spec $ spec/xml-transformer)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) ::u/collection)))        
      (st/encode output-spec $ spec/xml-transformer)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) :content first :content)))
      )))