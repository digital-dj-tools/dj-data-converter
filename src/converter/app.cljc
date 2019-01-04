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
  (xml-nml-spec [this])
  (convert [this traktor-nml]))

(def traktor->rekordbox
  (reify
    TraktorRekordboxConverter
    (xml-nml-spec
      [this]
      t/nml-spec)
    (convert
      [this traktor-nml]
      (c/traktor->rekordbox traktor-nml))))

(defn doto-prn
  [obj f]
  (prn (f obj)))

(s/def ::check-input boolean?)

(s/fdef convert-data
  :args (s/cat :nml-xml (spec/value-encoded-spec t/nml-spec spec/string-transformer)
               :config #{{:converter traktor->rekordbox}}
               :options (s/keys))
  :ret (spec/value-encoded-spec r/dj-playlists-xml-spec spec/string-transformer))
; TODO :ret spec should OR with some spec that checks all leafs are strings

(defn convert-data
  [xml config options]
  (let [nml-xml-spec (xml-nml-spec (:converter config))]
    (if
     (and (:check-input options) (s/invalid? (st/conform nml-xml-spec xml spec/string-transformer)))
      (let [explain (st/explain-data nml-xml-spec xml spec/string-transformer)
            data {:type ::convert
                  :problems (st/+problems+ explain)
                  :spec nml-xml-spec
                  :value xml}]
        (throw (ex-info "Spec conform error:" data)))
      (as-> xml $
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) :content first next next :content)))
        (spec/decode! t/nml-spec $ spec/xml-transformer)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) ::t/collection)))
        (convert (:converter config) $)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) ::r/collection)))
        (st/encode r/dj-playlists-xml-spec $ spec/xml-transformer)
    ; (doto $ (doto-prn (comp #(if (nil? %) % (realized? %)) :content first :content)))
        ))))