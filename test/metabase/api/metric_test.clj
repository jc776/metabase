(ns metabase.api.metric-test
  "Tests for /api/metric endpoints."
  (:require [expectations :refer :all]
            (metabase [http-client :as http]
                      [middleware :as middleware])
            (metabase.models [database :refer [Database]]
                             [revision :refer [Revision]]
                             [metric :refer [Metric], :as metric]
                             [table :refer [Table]])
            [metabase.test.data.users :refer :all]
            [metabase.test.data :refer :all]
            [metabase.test.util :as tu]))

;; ## Helper Fns

(defn- user-details [user]
  (tu/match-$ user
    {:id           $
     :email        $
     :date_joined  $
     :first_name   $
     :last_name    $
     :last_login   $
     :is_superuser $
     :is_qbnewb    $
     :common_name  $}))

(defn- metric-response [{:keys [created_at updated_at], :as metric}]
  (-> (into {} metric)
      (dissoc :id :table_id)
      (update :creator #(into {} %))
      (assoc :created_at (not (nil? created_at)))
      (assoc :updated_at (not (nil? updated_at)))))


;; ## /api/metric/* AUTHENTICATION Tests
;; We assume that all endpoints for a given context are enforced by the same middleware, so we don't run the same
;; authentication test on every single individual endpoint

(expect (get middleware/response-unauthentic :body) (http/client :get 401 "metric"))
(expect (get middleware/response-unauthentic :body) (http/client :put 401 "metric/13"))


;; ## POST /api/metric

;; test security.  requires superuser perms
(expect "You don't have permissions to do that."
  ((user->client :rasta) :post 403 "metric" {:name       "abc"
                                              :table_id   123
                                              :definition {}}))

;; test validations
(expect {:errors {:name "field is a required param."}}
  ((user->client :crowberto) :post 400 "metric" {}))

(expect {:errors {:table_id "field is a required param."}}
  ((user->client :crowberto) :post 400 "metric" {:name "abc"}))

(expect {:errors {:table_id "Invalid value 'foobar' for 'table_id': value must be an integer."}}
  ((user->client :crowberto) :post 400 "metric" {:name     "abc"
                                                  :table_id "foobar"}))

(expect {:errors {:definition "field is a required param."}}
  ((user->client :crowberto) :post 400 "metric" {:name     "abc"
                                                  :table_id 123}))

(expect {:errors {:definition "Invalid value 'foobar' for 'definition': value must be a dictionary."}}
  ((user->client :crowberto) :post 400 "metric" {:name       "abc"
                                                  :table_id   123
                                                  :definition "foobar"}))

(expect
  {:name         "A Metric"
   :description  "I did it!"
   :creator_id   (user->id :crowberto)
   :creator      (user-details (fetch-user :crowberto))
   :created_at   true
   :updated_at   true
   :is_active    true
   :definition   {:database 21
                  :query    {:filter ["abc"]}}}
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{:keys [id]} {:db_id database-id}]]
    (metric-response ((user->client :crowberto) :post 200 "metric" {:name        "A Metric"
                                                                    :description "I did it!"
                                                                    :table_id    id
                                                                    :definition  {:database 21
                                                                                  :query    {:filter ["abc"]}}}))))


;; ## PUT /api/metric

;; test security.  requires superuser perms
(expect "You don't have permissions to do that."
  ((user->client :rasta) :put 403 "metric/1" {:name             "abc"
                                               :definition       {}
                                               :revision_message "something different"}))

;; test validations
(expect {:errors {:name "field is a required param."}}
  ((user->client :crowberto) :put 400 "metric/1" {}))

(expect {:errors {:revision_message "field is a required param."}}
  ((user->client :crowberto) :put 400 "metric/1" {:name "abc"}))

(expect {:errors {:revision_message "Invalid value '' for 'revision_message': value must be a non-empty string."}}
  ((user->client :crowberto) :put 400 "metric/1" {:name             "abc"
                                                   :revision_message ""}))

(expect {:errors {:definition "field is a required param."}}
  ((user->client :crowberto) :put 400 "metric/1" {:name             "abc"
                                                   :revision_message "123"}))

(expect {:errors {:definition "Invalid value 'foobar' for 'definition': value must be a dictionary."}}
  ((user->client :crowberto) :put 400 "metric/1" {:name             "abc"
                                                   :revision_message "123"
                                                   :definition       "foobar"}))

(expect
  {:name         "Costa Rica"
   :description  nil
   :creator_id   (user->id :rasta)
   :creator      (user-details (fetch-user :rasta))
   :created_at   true
   :updated_at   true
   :is_active    true
   :definition   {:database 2
                  :query    {:filter ["not" "the toucans you're looking for"]}}}
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id :id} {:db_id database-id}]
                  Metric   [{:keys [id]} {:table_id table-id}]]
    (metric-response ((user->client :crowberto) :put 200 (format "metric/%d" id) {:id               id
                                                                                  :name             "Costa Rica"
                                                                                  :description      nil
                                                                                  :table_id         456
                                                                                  :revision_message "I got me some revisions"
                                                                                  :definition       {:database 2
                                                                                                     :query    {:filter ["not" "the toucans you're looking for"]}}}))))


;; ## DELETE /api/metric/:id

;; test security.  requires superuser perms
(expect "You don't have permissions to do that."
  ((user->client :rasta) :delete 403 "metric/1" :revision_message "yeeeehaw!"))


;; test validations
(expect {:errors {:revision_message "field is a required param."}}
  ((user->client :crowberto) :delete 400 "metric/1" {:name "abc"}))

(expect {:errors {:revision_message "Invalid value '' for 'revision_message': value must be a non-empty string."}}
  ((user->client :crowberto) :delete 400 "metric/1" :revision_message ""))

(expect
  [{:success true}
   {:name         "Toucans in the rainforest"
    :description  "Lookin' for a blueberry"
    :creator_id   (user->id :rasta)
    :creator      (user-details (fetch-user :rasta))
    :created_at   true
    :updated_at   true
    :is_active    false
    :definition   {}}]
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id :id} {:db_id database-id}]
                  Metric   [{:keys [id]}   {:table_id table-id}]]
    [((user->client :crowberto) :delete 200 (format "metric/%d" id) :revision_message "carryon")
     (metric-response (metric/retrieve-metric id))]))


;; ## GET /api/metric/:id

;; test security.  requires superuser perms
(expect "You don't have permissions to do that."
  ((user->client :rasta) :get 403 "metric/1"))


(expect
  {:name         "Toucans in the rainforest"
   :description  "Lookin' for a blueberry"
   :creator_id   (user->id :crowberto)
   :creator      (user-details (fetch-user :crowberto))
   :created_at   true
   :updated_at   true
   :is_active    true
   :definition   {}}
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id :id} {:db_id database-id}]
                  Metric   [{:keys [id]}   {:creator_id  (user->id :crowberto)
                                            :table_id    table-id}]]
    (metric-response ((user->client :crowberto) :get 200 (format "metric/%d" id)))))


;; ## GET /api/metric/:id/revisions

;; test security.  requires superuser perms
(expect "You don't have permissions to do that."
  ((user->client :rasta) :get 403 "metric/1/revisions"))


(expect
  [{:is_reversion false
    :is_creation  false
    :message      "updated"
    :user         (-> (user-details (fetch-user :crowberto))
                      (dissoc :email :date_joined :last_login :is_superuser :is_qbnewb))
    :diff         {:name {:before "b" :after "c"}}
    :description  "renamed this Metric from \"b\" to \"c\"."}
   {:is_reversion false
    :is_creation  true
    :message      nil
    :user         (-> (user-details (fetch-user :rasta))
                      (dissoc :email :date_joined :last_login :is_superuser :is_qbnewb))
    :diff         {:name       {:after "b"}
                   :definition {:after {:filter ["AND" [">" 1 25]]}}}
    :description  nil}]
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id :id} {:db_id database-id}]
                  Metric   [{:keys [id]}   {:creator_id  (user->id :crowberto)
                                            :table_id    table-id
                                            :name        "One Metric to rule them all, one metric to define them"
                                            :description "One metric to bring them all, and in the DataModel bind them"
                                            :definition  {:database 123
                                                          :query    {:filter ["In the Land of Metabase where the Datas lie"]}}}]
                  Revision [_              {:model        "Metric"
                                            :model_id     id
                                            :object       {:name "b"
                                                           :definition {:filter ["AND" [">" 1 25]]}}
                                            :is_creation  true}]
                  Revision [_              {:model        "Metric"
                                            :model_id     id
                                            :user_id      (user->id :crowberto)
                                            :object       {:name "c"
                                                           :definition {:filter ["AND" [">" 1 25]]}}
                                            :message      "updated"}]]
    (doall (for [revision ((user->client :crowberto) :get 200 (format "metric/%d/revisions" id))]
             (dissoc revision :timestamp :id)))))


;; ## POST /api/metric/:id/revert

;; test security.  requires superuser perms
(expect "You don't have permissions to do that."
  ((user->client :rasta) :post 403 "metric/1/revert" {:revision_id 56}))


(expect {:errors {:revision_id "field is a required param."}}
  ((user->client :crowberto) :post 400 "metric/1/revert" {}))

(expect {:errors {:revision_id "Invalid value 'foobar' for 'revision_id': value must be an integer."}}
  ((user->client :crowberto) :post 400 "metric/1/revert" {:revision_id "foobar"}))


(expect
  [ ;; the api response
   {:is_reversion true
    :is_creation  false
    :message      nil
    :user         (dissoc (user-details (fetch-user :crowberto)) :email :date_joined :last_login :is_superuser :is_qbnewb)
    :diff         {:name {:before "Changed Metric Name"
                          :after  "One Metric to rule them all, one metric to define them"}}
    :description  "renamed this Metric from \"Changed Metric Name\" to \"One Metric to rule them all, one metric to define them\"."}
   ;; full list of final revisions, first one should be same as the revision returned by the endpoint
   [{:is_reversion true
     :is_creation  false
     :message      nil
     :user         (dissoc (user-details (fetch-user :crowberto)) :email :date_joined :last_login :is_superuser :is_qbnewb)
     :diff         {:name {:before "Changed Metric Name"
                           :after  "One Metric to rule them all, one metric to define them"}}
     :description  "renamed this Metric from \"Changed Metric Name\" to \"One Metric to rule them all, one metric to define them\"."}
    {:is_reversion false
     :is_creation  false
     :message      "updated"
     :user         (dissoc (user-details (fetch-user :crowberto)) :email :date_joined :last_login :is_superuser :is_qbnewb)
     :diff         {:name {:after  "Changed Metric Name"
                           :before "One Metric to rule them all, one metric to define them"}}
     :description  "renamed this Metric from \"One Metric to rule them all, one metric to define them\" to \"Changed Metric Name\"."}
    {:is_reversion false
     :is_creation  true
     :message      nil
     :user         (dissoc (user-details (fetch-user :rasta)) :email :date_joined :last_login :is_superuser :is_qbnewb)
     :diff         {:name        {:after "One Metric to rule them all, one metric to define them"}
                    :description {:after "One metric to bring them all, and in the DataModel bind them"}
                    :definition  {:after {:database 123
                                          :query    {:filter ["In the Land of Metabase where the Datas lie"]}}}}
     :description  nil}]]
  (tu/with-temp* [Database [{database-id :id}]
                  Table    [{table-id :id}    {:db_id database-id}]
                  Metric   [{:keys [id]}      {:creator_id  (user->id :crowberto)
                                               :table_id    table-id
                                               :name        "One Metric to rule them all, one metric to define them"
                                               :description "One metric to bring them all, and in the DataModel bind them"
                                               :definition  {:creator_id  (user->id :crowberto)
                                                             :table_id    table-id
                                                             :name        "Reverted Metric Name"
                                                             :description nil
                                                             :definition  {:database 123
                                                                           :query    {:filter ["In the Land of Metabase where the Datas lie"]}}}}]
                  Revision [{revision-id :id} {:model        "Metric"
                                               :model_id     id
                                               :object       {:creator_id  (user->id :crowberto)
                                                              :table_id    table-id
                                                              :name        "One Metric to rule them all, one metric to define them"
                                                              :description "One metric to bring them all, and in the DataModel bind them"
                                                              :definition  {:database 123
                                                                            :query    {:filter ["In the Land of Metabase where the Datas lie"]}}}
                                               :is_creation  true}]
                  Revision [_                 {:model        "Metric"
                                               :model_id     id
                                               :user_id      (user->id :crowberto)
                                               :object       {:creator_id  (user->id :crowberto)
                                                              :table_id    table-id
                                                              :name        "Changed Metric Name"
                                                              :description "One metric to bring them all, and in the DataModel bind them"
                                                              :definition  {:database 123
                                                                            :query    {:filter ["In the Land of Metabase where the Datas lie"]}}}
                                               :message      "updated"}]]
    [(dissoc ((user->client :crowberto) :post 200 (format "metric/%d/revert" id) {:revision_id revision-id}) :id :timestamp)
     (doall (for [revision ((user->client :crowberto) :get 200 (format "metric/%d/revisions" id))]
              (dissoc revision :timestamp :id)))]))
