(ns converter.core
  (:require
   [converter.map :as map]
   [converter.traktor.core :as t]
   [converter.traktor.cue :as tc]
   [converter.rekordbox.core :as r]
   [converter.rekordbox.tempo :as rt]
   [converter.rekordbox.position-mark :as rp]
   [converter.spec :as spec]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   [spec-tools.core :as st]
   [spec-tools.transform :as stt]))

(defn traktor-album->rekordbox-track
  [{:keys [::t/album]} rekordbox-track _]
  (let [{:keys [::t/track ::t/album-title]} album]
    (cond-> rekordbox-track
      track (assoc ::r/track track)
      album-title (assoc ::r/album album-title))))

(defn traktor-info->rekordbox-track
  [{:keys [::t/info]} rekordbox-track _]
  (let [{:keys [::t/playtime]} info]
    (cond-> rekordbox-track
      playtime (assoc ::r/total-time playtime))))

(def traktor-cue-type->rekordbox
  {::tc/type-cue ::rp/type-cue
   ::tc/type-fade-in ::rp/type-cue
   ::tc/type-fade-out ::rp/type-cue
   ::tc/type-load ::rp/type-cue
   ::tc/type-grid ::rp/type-cue
   ::tc/type-loop ::rp/type-loop})

(def red [230 40 40])
(def green [60 235 80])
(def gold [255 140 0])
(def blue [48 90 255])
(def pink [222 68 207])

(def traktor-cue-type->rekordbox-colours {::tc/type-cue green
                                          ::tc/type-fade-in pink
                                          ::tc/type-fade-out pink
                                          ::tc/type-load blue
                                          ::tc/type-grid red
                                          ::tc/type-loop gold})

(defn millis->seconds
  [m]
  (/ m 1000))

(defn traktor-grid-cue->rekordbox
  [{:keys [::tc/start]} bpm]
  {::rt/inizio (millis->seconds start)
   ::rt/bpm bpm
   ::rt/metro "4/4"
   ::rt/battito "1"})

(defn traktor-cue->rekordbox
  [{:keys [::tc/name ::tc/type ::tc/start ::tc/len ::tc/hotcue]} hotcue?]
  (as-> {} $
    (assoc $
           ::rp/name name
           ::rp/type (traktor-cue-type->rekordbox type)
           ::rp/start (millis->seconds start)
           ::rp/end (millis->seconds (+ start len))
           ::rp/num (if hotcue? "-1" hotcue))
    (apply assoc $ (reduce-kv #(conj %1 %3 ((traktor-cue-type->rekordbox-colours type) %2))
                              []
                              [::rp/red ::rp/green ::rp/blue]))))

(defn traktor-cues->rekordbox-track
  [{:keys [::t/cues ::t/bpm]} rekordbox-track _]
  (-> (reduce #(-> %1
                   (update ::r/tempos
                           (fn [tempos cue] (if (and bpm (= ::tc/type-grid (cue ::tc/type)))
                                              (conj tempos (traktor-grid-cue->rekordbox cue bpm)) ; only one tempo/bpm value for the whole track, in traktor
                                              tempos)) %2)
                   (update ::r/position-marks
                           (fn [marks cue] (if (not= "-1" (cue ::tc/hotcue))
                                             (conj marks (traktor-cue->rekordbox cue false) (traktor-cue->rekordbox cue true))
                                             marks)) %2))
              rekordbox-track
              cues)
      (map/remove-nil ::r/tempos ::r/position-marks)))

(defn equiv-album?
  [{:keys [::t/album]} rekordbox-track _]
  (and (= (::t/track album) (::r/track rekordbox-track))
       (= (::t/album-title album) (::r/album rekordbox-track))))

; TODO implement equiv check
(defn equiv-info?
  [traktor-entry rekordbox-track _]
  true)

; TODO implement equiv check
(defn equiv-position-marks-and-tempo?
  [traktor-entry rekordbox-track _]
  true)

(s/fdef traktor-entry->rekordbox-track
  :args (s/cat :traktor-entry (spec/such-that-spec t/entry-spec #(contains? % ::t/info) 100))
  :ret r/track-spec
  :fn (fn equiv-rekordbox-track? [{:keys [] rekordbox-track :ret {:keys [traktor-entry]} :args}]
        (map/equiv? traktor-entry rekordbox-track
                    (partial map/equiv-key-ns (namespace ::r/unused))
                    {::t/album equiv-album?
                     ::t/info equiv-info?
                     ::t/bpm ::r/average-bpm
                     ::t/cues equiv-position-marks-and-tempo?
                     ::t/title ::r/name})))

(defn traktor-entry->rekordbox-track
  [{:keys [::t/title ::t/artist ::t/bpm] :as traktor-entry}]
  (map/transform traktor-entry
                 (partial map/transform-key-ns (namespace ::r/unused))
                 {::t/album traktor-album->rekordbox-track
                  ::t/info traktor-info->rekordbox-track
                  ::t/bpm ::r/average-bpm
                  ::t/cues traktor-cues->rekordbox-track ; TODO consider the case where there's a tempo, but no cues
                  ::t/title ::r/name}))

(s/fdef traktor->rekordbox
  :args (s/cat :traktor-nml t/nml-spec)
  :ret r/dj-playlists-spec
  :fn (fn equiv-collection-sizes? [{:keys [args ret]}]
        (let [traktor-nml (first (s/unform (s/cat :traktor-nml t/nml-spec) args))
              dj-playlists (s/unform r/dj-playlists-spec ret)]
          (= (count (->> traktor-nml ::t/collection (remove #(not (contains? % ::t/info)))))
             (count (->> dj-playlists ::r/collection))))))

(defn print-progress
  [f]
  (fn [idx itm]
    (when (= 0 (mod idx 1000))
      (println ".")
      #?(:clj (flush)))
    (f itm)))

(defn traktor->rekordbox
  [traktor-nml]
  {::r/collection (map-indexed 
                   (print-progress traktor-entry->rekordbox-track) 
                   (remove #(not (contains? % ::t/info)) (::t/collection traktor-nml)))})

(defn rekordbox->traktor
  [_ dj-playlists])
