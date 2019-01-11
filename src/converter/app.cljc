(ns converter.app
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [converter.core :as c]
   [converter.rekordbox.core :as r]
   [converter.traktor.core :as t]
   [converter.spec :as spec]
   [converter.xml :as xml]
   [spec-tools.core :as st]))

(defprotocol TraktorRekordboxConverter
  (input-spec [this])
  (output-spec [this]))

(def traktor->rekordbox
  (reify
    TraktorRekordboxConverter
    (input-spec
      [this]
      t/nml-spec)
    (output-spec
      [this]
      r/dj-playlists-spec)))

(defn doto-prn
  [obj f]
  (prn (f obj)))

(s/def ::check-input boolean?)

(s/fdef convert-data
  :args (s/cat :xml (spec/value-encoded-spec t/nml-spec spec/string-transformer)
               :config #{{:converter traktor->rekordbox}}
               :options (s/keys))
  :ret (spec/value-encoded-spec r/dj-playlists-spec spec/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert-data
  [xml config options]
  (let [input-spec (input-spec (:converter config))
        output-spec (output-spec (:converter config))]
    (if (and (:check-input options)
             (s/invalid? (st/conform input-spec xml spec/string-transformer)))
      (let [explain (st/explain-data input-spec xml spec/string-transformer)
            data {:type ::convert
                  :problems (st/+problems+ explain)
                  :spec input-spec
                  :value xml}]
        (throw (ex-info "Spec conform error:" data)))
      (as-> xml $
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) :content first next next :content)))
        (spec/decode! input-spec $ spec/string-transformer)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) :content first next next :content)))
        (spec/decode! t/library-spec $ spec/xml-transformer)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) ::u/collection)))        
        (st/encode output-spec $ spec/xml-transformer)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) :content first :content)))
        ))))