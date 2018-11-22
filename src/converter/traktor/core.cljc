(ns converter.traktor.core
  (:require
   [cemerick.url :as url]
   [clojure.data.zip.xml :as zx]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [clojure.string :as str]
   [clojure.test]
   [clojure.zip :as zip]
   [converter.map :as map]
   [converter.spec :as spec]
   [converter.traktor.cue :as tc]
   [converter.xml :as xml]
   [spec-tools.core :as st]
   [spec-tools.data-spec :as std]
   [spec-tools.spec :as sts]))

(def location-xml
  {:tag (s/spec #{:LOCATION})
   :attrs {:DIR ::spec/not-blank
           :FILE ::spec/not-blank
           (std/opt :VOLUME) (std/or {:drive-letter ::spec/drive-letter ; how can I say, if no volume also no volumeid?
                                      :not-drive-letter ::spec/not-blank})
           (std/opt :VOLUMEID) ::spec/not-blank}})

(def location-xml-spec
  (std/spec {:name ::location-xml
             :spec location-xml}))

(defn location->xml
  [{:keys [:path]}]
  (let [path (str/split path #"/")]
    {:tag :LOCATION
     :attrs {:DIR (str (str/join "/:" (drop-last path)) "/:")
             :FILE (last path)}}))

(s/fdef xml->location
  :args (s/cat :location-z (spec/xml-zip-spec location-xml-spec))
  :ret ::spec/url)

(defn xml->location
  [location-z]
  (let [dir (zx/attr location-z :DIR)
        file (zx/attr location-z :FILE)
        volume (zx/attr location-z :VOLUME)]
    (apply url/url (as-> [] $
                     (conj $ "file://localhost")
                     (conj $ (if (spec/drive-letter? volume) (str "/" volume) ""))
                     (reduce conj $ (map url/url-encode (str/split dir #"/:")))
                     (conj $ (url/url-encode file))))))

(s/def ::TRACK string?)

(s/def ::TITLE string?)

(def entry-xml
  {:tag (s/spec #{:ENTRY})
   :attrs {(std/opt :TITLE) string?
           (std/opt :ARTIST) string?}
   :content      (s/cat
                  :location-xml location-xml-spec
                  :album-xml (s/? (std/spec {:name ::album-xml
                                             :spec {:tag (s/spec #{:ALBUM})
                                                    :attrs (s/keys :req-un [(or ::TRACK ::TITLE)])}}))
                  :modification-info (s/? (std/spec {:name ::modification-info-xml
                                                     :spec {:tag (s/spec #{:MODIFICATION_INFO})}}))
                  :info-xml (s/? (std/spec {:name ::info-xml
                                            :spec {:tag (s/spec #{:INFO})
                                                   :attrs {(std/opt :PLAYTIME) string?}}}))
                  :tempo-xml (s/? (std/spec {:name ::tempo-xml
                                             :spec {:tag (s/spec #{:TEMPO})
                                                    :attrs {(std/opt :BPM) string?}}}))
                  :loudness-xml (s/? (std/spec {:name ::loudness-xml
                                                :spec {:tag (s/spec #{:LOUDNESS})}}))
                  :musical-key-xml (s/? (std/spec {:name ::musical-key-xml
                                                   :spec {:tag (s/spec #{:MUSICAL_KEY})}}))
                  :loopinfo-xml (s/? (std/spec {:name ::loopinfo-xml
                                                :spec {:tag (s/spec #{:LOOPINFO})}}))
                  :cue-xml (s/* tc/cue-xml-spec))})

(def entry-xml-spec
  (std/spec
   {:name ::entry-xml
    :spec entry-xml}))

(s/def ::track string?)

(s/def ::album-title string?)

(def entry
  {::location ::spec/url
   (std/opt ::title) string?
   (std/opt ::artist) string?
   (std/opt ::album) (s/keys :req [(or ::track ::album-title)]) ; std/or doesn't work like s/or..
   (std/opt ::info) {::playtime string?}
   (std/opt ::bpm) string?
   (std/opt ::cues) [tc/cue-spec] ; how can I say, coll must not be empty?
      ;  (std/opt ::cues) (s/cat :cues (s/+ tc/cue-spec)) ; this avoids an empty coll, but st/decode & st/coerce don't work :(
})

(def entry-spec
  (-> (std/spec
       {:name ::entry
        :spec entry})
      (spec/remove-empty-spec ::cues)))

(s/fdef entry->xml
  :args (s/cat :entry entry-spec)
  :ret entry-xml-spec)

(defn entry->xml
  [{:keys [::location ::title ::artist ::album ::info ::bpm ::cues]
    {:keys [::track ::album-title]} ::album {:keys [::playtime]} ::info}]
  {:tag :ENTRY
   :attrs (cond-> {}
            title (assoc :TITLE title)
            artist (assoc :ARTIST artist))
   :content (cond-> []
              true (conj (location->xml location))
              (or track album-title) (conj {:tag :ALBUM
                                            :attrs (cond-> {}
                                                     track (assoc :TRACK track)
                                                     album-title (assoc :TITLE album-title))})
              playtime (conj {:tag :INFO
                              :attrs {:PLAYTIME playtime}})
              bpm (conj {:tag :TEMPO
                         :attrs {:BPM bpm}})
              cues (concat (map tc/cue->xml cues)))})

(s/fdef xml->entry
  :args (s/cat :entry-z (spec/xml-zip-spec entry-xml-spec))
  :ret entry-spec)

(defn xml->entry
  [entry-z]
  (let [title (zx/attr entry-z :TITLE)
        artist (zx/attr entry-z :ARTIST)
        album-z (zx/xml1-> entry-z :ALBUM)
        track (and album-z (zx/attr album-z :TRACK))
        album-title (and album-z (zx/attr album-z :TITLE))
        info-z (zx/xml1-> entry-z :INFO)
        playtime (and info-z (zx/attr info-z :PLAYTIME))
        tempo-z (zx/xml1-> entry-z :TEMPO)
        bpm (and tempo-z (zx/attr tempo-z :BPM))
        cues-z (zx/xml-> entry-z :CUE_V2)]
    (cond-> {::location (xml->location (zx/xml1-> entry-z :LOCATION))}
      title (assoc ::title title)
      artist (assoc ::artist artist)
      (or track album-title) (assoc ::album (cond-> {}
                                              track (assoc ::track track)
                                              album-title (assoc ::album-title album-title)))
      playtime (assoc ::info {::playtime playtime})
      bpm (assoc ::bpm bpm)
      (not (empty? cues-z)) (assoc ::cues (map tc/xml->cue cues-z)))))

(defn nml->xml
  [_ nml]
  {:tag :NML
   :attrs {:VERSION (::version nml)}
   :content [{:tag :COLLECTION :content (map entry->xml (::collection nml))}]})

(defn xml->nml
  [_ nml-xml]
  (if (xml/xml? nml-xml)
    (let [nml-z (zip/xml-zip nml-xml)
          collection-z (zx/xml1-> nml-z :COLLECTION)]
      {::version (zx/attr nml-z :VERSION)
       ::collection (map xml->entry (zx/xml-> collection-z :ENTRY))})
    nml-xml))

(def nml-xml
  {:tag (s/spec #{:NML})
   :attrs {:VERSION (st/spec #{19} {:type :long})}
   :content (s/cat
             :head-xml (s/? (std/spec {:name ::head-xml
                                       :spec {:tag (s/spec #{:HEAD})}}))
             :musicfolders-xml (s/? (std/spec {:name ::musicfolders-xml
                                               :spec {:tag (s/spec #{:MUSICFOLDERS})}}))
             :collection-xml (std/spec
                              {:name ::collection-xml
                               :spec {:tag (s/spec #{:COLLECTION})
                                      :content (s/cat :entries-xml (s/* entry-xml-spec))}})
             :sets-xml (s/? (std/spec {:name ::sets-xml
                                       :spec {:tag (s/spec #{:SETS})}}))
             :playlists-xml (s/? (std/spec {:name ::playlists-xml
                                            :spec {:tag (s/spec #{:PLAYLISTS})}}))
             :sorting-order-xml (s/* (std/spec {:name ::sorting-order-xml
                                                :spec {:tag (s/spec #{:SORTING_ORDER})}})))})


(def nml-xml-spec
  (->
   (std/spec
    {:name ::nml-xml
     :spec nml-xml})
   (assoc :encode/xml nml->xml)))

(def nml
  {::version pos-int?
   ::collection (s/cat :entries (s/* entry-spec))})

(def nml-spec
  (->
   (std/spec
    {:name ::nml
     :spec nml})
   (assoc
    :decode/xml xml->nml)))
