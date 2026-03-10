CREATE TABLE public.abuse_events (
                                     created_at timestamp(6) with time zone NOT NULL,
                                     id uuid NOT NULL,
                                     user_id uuid,
                                     description text,
                                     event_type character varying(255) NOT NULL,
                                     ip_address character varying(255),
                                     CONSTRAINT abuse_events_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['RAPID_TIPPING'::character varying, 'MESSAGE_SPAM'::character varying, 'LOGIN_BRUTE_FORCE'::character varying, 'MULTI_ACCOUNT_BEHAVIOR'::character varying, 'SUSPICIOUS_API_USAGE'::character varying, 'RESTRICTION_ESCALATED'::character varying])::text[])))
);


--
-- Name: abuse_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.abuse_reports (
                                      created_at timestamp(6) with time zone NOT NULL,
                                      id bigint NOT NULL,
                                      reporter_id bigint NOT NULL,
                                      target_user_id bigint,
                                      target_stream_id uuid,
                                      reason character varying(255) NOT NULL,
                                      status character varying(255) NOT NULL,
                                      CONSTRAINT abuse_reports_reason_check CHECK (((reason)::text = ANY ((ARRAY['UNDERAGE'::character varying, 'COPYRIGHT'::character varying, 'HARASSMENT'::character varying, 'VIOLENCE'::character varying, 'NON_CONSENSUAL'::character varying, 'SPAM'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT abuse_reports_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'UNDER_REVIEW'::character varying, 'RESOLVED'::character varying])::text[])))
);


--
-- Name: abuse_reports_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.abuse_reports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: abuse_reports_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.abuse_reports_id_seq OWNED BY public.abuse_reports.id;


--
-- Name: aml_incidents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.aml_incidents (
                                      created_at timestamp(6) with time zone NOT NULL,
                                      id uuid NOT NULL,
                                      user_id uuid NOT NULL,
                                      description text,
                                      risk_level character varying(255) NOT NULL
);


--
-- Name: aml_risk_scores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.aml_risk_scores (
                                        score integer NOT NULL,
                                        last_evaluated_at timestamp(6) with time zone NOT NULL,
                                        id uuid NOT NULL,
                                        user_id uuid NOT NULL,
                                        level character varying(255) NOT NULL
);


--
-- Name: aml_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.aml_rules (
                                  enabled boolean NOT NULL,
                                  threshold integer NOT NULL,
                                  id uuid NOT NULL,
                                  code character varying(255) NOT NULL,
                                  description character varying(255) NOT NULL
);


--
-- Name: analytics_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.analytics_events (
                                         created_at timestamp(6) with time zone NOT NULL,
                                         user_id bigint,
                                         id uuid NOT NULL,
                                         event_type character varying(255) NOT NULL,
                                         funnel_id character varying(255),
                                         metadata jsonb,
                                         CONSTRAINT analytics_events_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['VISIT'::character varying, 'USER_REGISTERED'::character varying, 'USER_LOGIN_SUCCESS'::character varying, 'USER_LOGIN_FAILED'::character varying, 'SUBSCRIPTION_STARTED'::character varying, 'SUBSCRIPTION_CANCELED'::character varying, 'PAYMENT_SUCCEEDED'::character varying, 'PAYMENT_FAILED'::character varying, 'EXPERIMENT_ASSIGNED'::character varying, 'PPV_CHAT_ACCESS_GRANTED'::character varying, 'PPV_CHAT_MESSAGE_BLOCKED'::character varying, 'SUPERTIP_SENT'::character varying, 'SLOW_MODE_BYPASS_GRANTED'::character varying, 'SLOW_MODE_BYPASS_EXPIRED'::character varying, 'SLOW_MODE_BYPASS_REVOKED'::character varying, 'HIGHLIGHTED_MESSAGE_SENT'::character varying, 'HIGHLIGHTED_MESSAGE_REFUNDED'::character varying, 'CHAT_MODE_CHANGED'::character varying, 'STREAM_JOIN'::character varying, 'STREAM_LEAVE'::character varying, 'CHAT_MESSAGE_SENT'::character varying])::text[])))
);


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
                                   created_at timestamp(6) with time zone NOT NULL,
                                   actor_user_id uuid,
                                   id uuid NOT NULL,
                                   target_id uuid,
                                   action character varying(255) NOT NULL,
                                   ip_address character varying(255),
                                   metadata text,
                                   target_type character varying(255) NOT NULL,
                                   user_agent character varying(255)
);


--
-- Name: badges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.badges (
                               duration_days integer,
                               created_at timestamp(6) with time zone,
                               token_cost bigint NOT NULL,
                               id uuid NOT NULL,
                               name character varying(255) NOT NULL
);


--
-- Name: chargeback_audits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chargeback_audits (
                                          cluster_size integer,
                                          created_at timestamp(6) with time zone NOT NULL,
                                          creator_id bigint,
                                          chargeback_id uuid NOT NULL,
                                          id uuid NOT NULL,
                                          user_id uuid NOT NULL,
                                          actions_taken text,
                                          reason text,
                                          risk_level character varying(255)
);


--
-- Name: chargeback_cases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chargeback_cases (
                                         amount numeric(38,2) NOT NULL,
                                         fraud_score_at_time integer NOT NULL,
                                         created_at timestamp(6) with time zone NOT NULL,
                                         updated_at timestamp(6) with time zone,
                                         id uuid NOT NULL,
                                         user_id uuid NOT NULL,
                                         currency character varying(255) NOT NULL,
                                         payment_intent_id character varying(255) NOT NULL,
                                         reason character varying(255),
                                         status character varying(255) NOT NULL,
                                         CONSTRAINT chargeback_cases_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'UNDER_REVIEW'::character varying, 'WON'::character varying, 'LOST'::character varying])::text[])))
);


--
-- Name: chargebacks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chargebacks (
                                    amount numeric(12,2),
                                    created_at timestamp(6) with time zone NOT NULL,
                                    id bigint NOT NULL,
                                    resolved_at timestamp(6) with time zone,
                                    user_id bigint NOT NULL,
                                    currency character varying(255),
                                    reason character varying(255),
                                    status character varying(255),
                                    stripe_charge_id character varying(255) NOT NULL,
                                    CONSTRAINT chargebacks_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'WON'::character varying, 'LOST'::character varying])::text[])))
);


--
-- Name: chargebacks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chargebacks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chargebacks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chargebacks_id_seq OWNED BY public.chargebacks.id;


--
-- Name: chat_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_messages (
                                      created_at timestamp(6) with time zone NOT NULL,
                                      id bigint NOT NULL,
                                      room_id bigint NOT NULL,
                                      sender_id bigint NOT NULL,
                                      content text,
                                      sender_role character varying(255)
);


--
-- Name: chat_messages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chat_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_messages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chat_messages_id_seq OWNED BY public.chat_messages.id;


--
-- Name: chat_moderations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_moderations (
                                         created_at timestamp(6) with time zone NOT NULL,
                                         expires_at timestamp(6) with time zone,
                                         id bigint NOT NULL,
                                         moderator_id bigint NOT NULL,
                                         target_user_id bigint,
                                         action character varying(255) NOT NULL,
                                         message_id character varying(255),
                                         reason character varying(255),
                                         room_id character varying(255),
                                         CONSTRAINT chat_moderations_action_check CHECK (((action)::text = ANY ((ARRAY['MUTE'::character varying, 'BAN'::character varying, 'DELETE_MESSAGE'::character varying, 'SHADOW_MUTE'::character varying])::text[])))
);


--
-- Name: chat_moderations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chat_moderations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_moderations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chat_moderations_id_seq OWNED BY public.chat_moderations.id;


--
-- Name: chat_rooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_rooms (
                                   is_live boolean NOT NULL,
                                   is_paid boolean NOT NULL,
                                   is_private boolean NOT NULL,
                                   created_at timestamp(6) with time zone NOT NULL,
                                   created_by bigint NOT NULL,
                                   price_per_message bigint,
                                   id uuid NOT NULL,
                                   ppv_content_id uuid,
                                   chat_mode character varying(255) NOT NULL,
                                   name character varying(255) NOT NULL,
                                   CONSTRAINT chat_rooms_chat_mode_check CHECK (((chat_mode)::text = ANY ((ARRAY['PUBLIC'::character varying, 'SUBSCRIBERS_ONLY'::character varying, 'CREATORS_ONLY'::character varying, 'MODERATORS_ONLY'::character varying])::text[])))
);


--
-- Name: chat_rooms_v2; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_rooms_v2 (
                                      is_live boolean NOT NULL,
                                      activated_at timestamp(6) with time zone,
                                      created_at timestamp(6) with time zone NOT NULL,
                                      creator_id bigint NOT NULL,
                                      id bigint NOT NULL,
                                      status character varying(255) NOT NULL,
                                      CONSTRAINT chat_rooms_v2_status_check CHECK (((status)::text = ANY ((ARRAY['WAITING_FOR_CREATOR'::character varying, 'ACTIVE'::character varying, 'PAUSED'::character varying, 'ENDED'::character varying])::text[])))
);


--
-- Name: chat_rooms_v2_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chat_rooms_v2_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_rooms_v2_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chat_rooms_v2_id_seq OWNED BY public.chat_rooms_v2.id;


--
-- Name: chat_violation_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.chat_violation_logs (
                                            creator_id bigint NOT NULL,
                                            id bigint NOT NULL,
                                            "timestamp" timestamp(6) with time zone NOT NULL,
                                            user_id bigint NOT NULL,
                                            message character varying(1000) NOT NULL,
                                            severity character varying(255) NOT NULL,
                                            CONSTRAINT chat_violation_logs_severity_check CHECK (((severity)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying])::text[])))
);


--
-- Name: chat_violation_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chat_violation_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_violation_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chat_violation_logs_id_seq OWNED BY public.chat_violation_logs.id;


--
-- Name: content; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content (
                                disabled boolean NOT NULL,
                                created_at timestamp(6) with time zone,
                                creator_id bigint NOT NULL,
                                id uuid NOT NULL,
                                access_level character varying(255) NOT NULL,
                                description text,
                                media_url character varying(255),
                                thumbnail_url character varying(255),
                                title character varying(255) NOT NULL,
                                CONSTRAINT content_access_level_check CHECK (((access_level)::text = ANY ((ARRAY['FREE'::character varying, 'PREMIUM'::character varying, 'CREATOR'::character varying])::text[])))
);


--
-- Name: content_analytics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_analytics (
                                          date date NOT NULL,
                                          revenue numeric(19,2) NOT NULL,
                                          content_id uuid NOT NULL,
                                          creator_id uuid NOT NULL,
                                          id uuid NOT NULL,
                                          content_type character varying(255) NOT NULL
);


--
-- Name: creator_analytics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_analytics (
                                          date date NOT NULL,
                                          live_stream_earnings numeric(19,2) NOT NULL,
                                          messages_per_viewer double precision NOT NULL,
                                          ppv_earnings numeric(19,2) NOT NULL,
                                          subscription_earnings numeric(19,2) NOT NULL,
                                          tips_earnings numeric(19,2) NOT NULL,
                                          total_earnings numeric(19,2) NOT NULL,
                                          avg_session_duration bigint NOT NULL,
                                          returning_viewers bigint NOT NULL,
                                          subscriptions_count bigint NOT NULL,
                                          total_views bigint NOT NULL,
                                          unique_viewers bigint NOT NULL,
                                          creator_id uuid NOT NULL,
                                          id uuid NOT NULL
);


--
-- Name: creator_collusion_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_collusion_records (
                                                  score integer NOT NULL,
                                                  evaluated_at timestamp(6) with time zone,
                                                  creator_id uuid NOT NULL,
                                                  id uuid NOT NULL,
                                                  detected_pattern text
);


--
-- Name: creator_earnings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_earnings (
                                         available_tokens bigint NOT NULL,
                                         locked_tokens bigint NOT NULL,
                                         total_earned_tokens bigint NOT NULL,
                                         updated_at timestamp(6) with time zone NOT NULL,
                                         user_id bigint NOT NULL,
                                         id uuid NOT NULL
);


--
-- Name: creator_earnings_balances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_earnings_balances (
                                                  available_balance numeric(38,2) NOT NULL,
                                                  payouts_disabled boolean NOT NULL,
                                                  pending_balance numeric(38,2) NOT NULL,
                                                  total_earned numeric(38,2) NOT NULL,
                                                  created_at timestamp(6) with time zone NOT NULL,
                                                  creator_id bigint NOT NULL,
                                                  updated_at timestamp(6) with time zone NOT NULL,
                                                  id uuid NOT NULL
);


--
-- Name: creator_earnings_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_earnings_history (
                                                 dry_run boolean NOT NULL,
                                                 gross_amount numeric(38,2) NOT NULL,
                                                 locked boolean NOT NULL,
                                                 net_amount numeric(38,2) NOT NULL,
                                                 platform_fee numeric(38,2) NOT NULL,
                                                 created_at timestamp(6) with time zone NOT NULL,
                                                 creator_id bigint NOT NULL,
                                                 user_id bigint,
                                                 hold_policy_id uuid,
                                                 id uuid NOT NULL,
                                                 invoice_id uuid,
                                                 payout_hold_id uuid,
                                                 payout_id uuid,
                                                 payout_request_id uuid,
                                                 currency character varying(255) NOT NULL,
                                                 source_type character varying(255) NOT NULL,
                                                 stripe_charge_id character varying(255),
                                                 stripe_session_id character varying(255),
                                                 CONSTRAINT creator_earnings_history_source_type_check CHECK (((source_type)::text = ANY ((ARRAY['SUBSCRIPTION'::character varying, 'TIP'::character varying, 'PPV'::character varying, 'HIGHLIGHTED_CHAT'::character varying, 'CHAT'::character varying, 'PRIVATE_SHOW'::character varying])::text[])))
);


--
-- Name: creator_earnings_invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_earnings_invoices (
                                                  gross_earnings numeric(19,2) NOT NULL,
                                                  net_earnings numeric(19,2) NOT NULL,
                                                  platform_fee numeric(19,2) NOT NULL,
                                                  created_at timestamp(6) with time zone NOT NULL,
                                                  creator_id bigint NOT NULL,
                                                  period_end timestamp(6) with time zone NOT NULL,
                                                  period_start timestamp(6) with time zone NOT NULL,
                                                  id uuid NOT NULL,
                                                  creator_address character varying(255),
                                                  creator_email character varying(255),
                                                  creator_name character varying(255),
                                                  currency character varying(255) NOT NULL,
                                                  invoice_number character varying(255) NOT NULL,
                                                  seller_address character varying(255),
                                                  seller_email character varying(255),
                                                  seller_name character varying(255),
                                                  seller_vat_number character varying(255),
                                                  status character varying(255) NOT NULL,
                                                  CONSTRAINT creator_earnings_invoices_status_check CHECK (((status)::text = ANY ((ARRAY['GENERATED'::character varying, 'PAID'::character varying])::text[])))
);


--
-- Name: creator_follow; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_follow (
                                       created_at timestamp(6) with time zone NOT NULL,
                                       creator_id bigint NOT NULL,
                                       follower_id bigint NOT NULL,
                                       id bigint NOT NULL
);


--
-- Name: creator_follow_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.creator_follow_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: creator_follow_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.creator_follow_id_seq OWNED BY public.creator_follow.id;


--
-- Name: creator_moderation_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_moderation_settings (
                                                    ai_highlight_enabled boolean NOT NULL,
                                                    auto_pin_large_tips boolean NOT NULL,
                                                    strict_mode boolean NOT NULL,
                                                    creator_user_id bigint NOT NULL,
                                                    banned_words text
);


--
-- Name: creator_monetization; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_monetization (
                                             subscription_price numeric(19,4) NOT NULL,
                                             tip_enabled boolean NOT NULL,
                                             created_at timestamp(6) with time zone,
                                             creator_profile_id bigint NOT NULL,
                                             id bigint NOT NULL,
                                             updated_at timestamp(6) with time zone
);


--
-- Name: creator_monetization_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.creator_monetization_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: creator_monetization_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.creator_monetization_id_seq OWNED BY public.creator_monetization.id;


--
-- Name: creator_payout_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_payout_settings (
                                                enabled boolean NOT NULL,
                                                minimum_payout_amount numeric(19,2),
                                                created_at timestamp(6) with time zone NOT NULL,
                                                updated_at timestamp(6) with time zone NOT NULL,
                                                creator_id uuid NOT NULL,
                                                id uuid NOT NULL,
                                                payout_method character varying(255) NOT NULL,
                                                stripe_account_id character varying(255),
                                                CONSTRAINT creator_payout_settings_payout_method_check CHECK (((payout_method)::text = ANY ((ARRAY['STRIPE_SEPA'::character varying, 'STRIPE_CARD'::character varying])::text[])))
);


--
-- Name: creator_payout_states; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_payout_states (
                                              current_limit numeric(19,2),
                                              manual_override boolean NOT NULL,
                                              updated_at timestamp(6) with time zone NOT NULL,
                                              creator_id uuid NOT NULL,
                                              id uuid NOT NULL,
                                              frequency character varying(255) NOT NULL,
                                              status character varying(255) NOT NULL,
                                              CONSTRAINT creator_payout_states_frequency_check CHECK (((frequency)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'PAUSED'::character varying, 'NO_LIMIT'::character varying])::text[]))),
    CONSTRAINT creator_payout_states_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'LIMITED'::character varying, 'PAUSED'::character varying])::text[])))
);


--
-- Name: creator_payouts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_payouts (
                                        amount numeric(19,2) NOT NULL,
                                        completed_at timestamp(6) with time zone,
                                        created_at timestamp(6) with time zone NOT NULL,
                                        creator_id uuid NOT NULL,
                                        id uuid NOT NULL,
                                        currency character varying(255) NOT NULL,
                                        failure_reason character varying(255),
                                        status character varying(255) NOT NULL,
                                        stripe_transfer_id character varying(255),
                                        CONSTRAINT creator_payouts_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: creator_posts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_posts (
                                      created_at timestamp(6) with time zone NOT NULL,
                                      creator_id bigint NOT NULL,
                                      id uuid NOT NULL,
                                      title character varying(120) NOT NULL,
                                      content text NOT NULL
);


--
-- Name: creator_presence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_presence (
                                         online boolean NOT NULL,
                                         creator_id bigint NOT NULL,
                                         id bigint NOT NULL,
                                         last_seen timestamp(6) with time zone NOT NULL
);


--
-- Name: creator_presence_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.creator_presence_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: creator_presence_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.creator_presence_id_seq OWNED BY public.creator_presence.id;


--
-- Name: creator_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_profiles (
                                         created_at timestamp(6) with time zone,
                                         id bigint NOT NULL,
                                         user_id bigint NOT NULL,
                                         avatar_url character varying(512),
                                         banner_url character varying(512),
                                         bio text,
                                         display_name character varying(255),
                                         public_handle character varying(255),
                                         status character varying(255) NOT NULL,
                                         username character varying(255),
                                         visibility character varying(255) NOT NULL,
                                         CONSTRAINT creator_profiles_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying])::text[]))),
    CONSTRAINT creator_profiles_visibility_check CHECK (((visibility)::text = ANY ((ARRAY['PUBLIC'::character varying, 'PRIVATE'::character varying])::text[])))
);


--
-- Name: creator_profiles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.creator_profiles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: creator_profiles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.creator_profiles_id_seq OWNED BY public.creator_profiles.id;


--
-- Name: creator_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_records (
                                        active boolean NOT NULL,
                                        id bigint NOT NULL,
                                        user_id bigint NOT NULL,
                                        bio text,
                                        profile_image_url character varying(255)
);


--
-- Name: creator_records_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.creator_records_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: creator_records_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.creator_records_id_seq OWNED BY public.creator_records.id;


--
-- Name: creator_reputation_snapshot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_reputation_snapshot (
                                                    current_score integer NOT NULL,
                                                    last_decay_at timestamp(6) with time zone,
                                                    last_positive_event_at timestamp(6) with time zone,
                                                    updated_at timestamp(6) with time zone NOT NULL,
                                                    creator_id uuid NOT NULL,
                                                    status character varying(255) NOT NULL,
                                                    CONSTRAINT creator_reputation_snapshot_current_score_check CHECK (((current_score <= 100) AND (current_score >= 0))),
                                                    CONSTRAINT creator_reputation_snapshot_status_check CHECK (((status)::text = ANY ((ARRAY['TRUSTED'::character varying, 'NORMAL'::character varying, 'WATCHED'::character varying, 'RESTRICTED'::character varying])::text[])))
);


--
-- Name: creator_stats; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_stats (
                                      today_net_earnings numeric(19,2) NOT NULL,
                                      total_net_earnings numeric(19,2) NOT NULL,
                                      highlights_count bigint NOT NULL,
                                      subscription_count bigint NOT NULL,
                                      tips_count bigint NOT NULL,
                                      today_net_tokens bigint NOT NULL,
                                      total_net_tokens bigint NOT NULL,
                                      updated_at timestamp(6) with time zone NOT NULL,
                                      creator_id uuid NOT NULL
);


--
-- Name: creator_stripe_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_stripe_accounts (
                                                onboarding_completed boolean NOT NULL,
                                                created_at timestamp(6) with time zone,
                                                creator_id bigint NOT NULL,
                                                id bigint NOT NULL,
                                                stripe_account_id character varying(255) NOT NULL
);


--
-- Name: creator_stripe_accounts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.creator_stripe_accounts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: creator_stripe_accounts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.creator_stripe_accounts_id_seq OWNED BY public.creator_stripe_accounts.id;


--
-- Name: creator_tips; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_tips (
                                     amount numeric(38,2) NOT NULL,
                                     created_at timestamp(6) with time zone NOT NULL,
                                     creator_id bigint NOT NULL,
                                     user_id bigint NOT NULL,
                                     id uuid NOT NULL,
                                     currency character varying(255) NOT NULL,
                                     status character varying(255) NOT NULL,
                                     stripe_session_id character varying(255),
                                     CONSTRAINT creator_tips_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: creator_top_content; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_top_content (
                                            rank integer NOT NULL,
                                            total_revenue numeric(19,2) NOT NULL,
                                            updated_at timestamp(6) with time zone NOT NULL,
                                            content_id uuid NOT NULL,
                                            creator_id uuid NOT NULL,
                                            id uuid NOT NULL,
                                            content_type character varying(255) NOT NULL,
                                            title character varying(255) NOT NULL
);


--
-- Name: creator_verification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creator_verification (
                                             date_of_birth date NOT NULL,
                                             created_at timestamp(6) with time zone NOT NULL,
                                             creator_id bigint NOT NULL,
                                             id bigint NOT NULL,
                                             updated_at timestamp(6) with time zone NOT NULL,
                                             country character varying(255) NOT NULL,
                                             document_back_url character varying(255),
                                             document_front_url character varying(255) NOT NULL,
                                             document_type character varying(255) NOT NULL,
                                             legal_first_name character varying(255) NOT NULL,
                                             legal_last_name character varying(255) NOT NULL,
                                             rejection_reason character varying(255),
                                             selfie_url character varying(255) NOT NULL,
                                             status character varying(255) NOT NULL,
                                             CONSTRAINT creator_verification_document_type_check CHECK (((document_type)::text = ANY ((ARRAY['PASSPORT'::character varying, 'ID_CARD'::character varying, 'DRIVER_LICENSE'::character varying])::text[]))),
    CONSTRAINT creator_verification_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'UNDER_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'SUSPENDED'::character varying])::text[])))
);


--
-- Name: creator_verification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.creator_verification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: creator_verification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.creator_verification_id_seq OWNED BY public.creator_verification.id;


--
-- Name: creators; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.creators (
                                 payout_frozen boolean NOT NULL,
                                 frozen_at timestamp(6) with time zone,
                                 creator_id uuid NOT NULL,
                                 id uuid NOT NULL,
                                 freeze_reason character varying(255)
);


--
-- Name: device_fingerprints; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_fingerprints (
                                            trusted boolean NOT NULL,
                                            first_seen timestamp(6) with time zone NOT NULL,
                                            last_seen timestamp(6) with time zone NOT NULL,
                                            user_id bigint NOT NULL,
                                            id uuid NOT NULL,
                                            fingerprint_hash character varying(255) NOT NULL,
                                            ip_address character varying(255),
                                            user_agent character varying(255)
);


--
-- Name: experiment_analytics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.experiment_analytics (
                                             count bigint NOT NULL,
                                             updated_at timestamp(6) with time zone NOT NULL,
                                             experiment_key character varying(255) NOT NULL,
                                             variant character varying(255) NOT NULL
);


--
-- Name: export_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.export_jobs (
                                    completed_at timestamp(6) with time zone,
                                    created_at timestamp(6) with time zone,
                                    id bigint NOT NULL,
                                    requested_by_admin_id bigint,
                                    error_message character varying(255),
                                    file_path character varying(255),
                                    status character varying(255),
                                    type character varying(255)
);


--
-- Name: export_jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.export_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: export_jobs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.export_jobs_id_seq OWNED BY public.export_jobs.id;


--
-- Name: feature_flags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feature_flags (
                                      enabled boolean NOT NULL,
                                      is_experiment boolean NOT NULL,
                                      rollout_percentage integer NOT NULL,
                                      created_at timestamp(6) with time zone NOT NULL,
                                      updated_at timestamp(6) with time zone NOT NULL,
                                      id uuid NOT NULL,
                                      environment character varying(255) NOT NULL,
                                      flag_key character varying(255) NOT NULL,
                                      CONSTRAINT feature_flags_environment_check CHECK (((environment)::text = ANY ((ARRAY['DEV'::character varying, 'STAGING'::character varying, 'PROD'::character varying])::text[])))
);


--
-- Name: fraud_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fraud_decisions (
                                        score integer NOT NULL,
                                        created_at timestamp(6) with time zone NOT NULL,
                                        related_tip_id bigint,
                                        id uuid NOT NULL,
                                        user_id uuid NOT NULL,
                                        risk_level character varying(50) NOT NULL,
                                        reasons character varying(255),
                                        CONSTRAINT fraud_decisions_risk_level_check CHECK (((risk_level)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[])))
);


--
-- Name: fraud_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fraud_events (
                                     created_at timestamp(6) with time zone NOT NULL,
                                     id uuid NOT NULL,
                                     user_id uuid NOT NULL,
                                     event_type character varying(255) NOT NULL,
                                     metadata text,
                                     reason character varying(255),
                                     CONSTRAINT fraud_events_event_type_check CHECK (((event_type)::text = ANY ((ARRAY['CHARGEBACK_REPORTED'::character varying, 'PAYOUT_FROZEN'::character varying, 'ACCOUNT_SUSPENDED'::character varying, 'ACCOUNT_TERMINATED'::character varying, 'MANUAL_OVERRIDE'::character varying, 'PAYMENT_SUCCESS'::character varying, 'CRITICAL_RISK_DETECTED'::character varying])::text[])))
);


--
-- Name: fraud_flags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fraud_flags (
                                    score integer,
                                    created_at timestamp(6) with time zone NOT NULL,
                                    id uuid NOT NULL,
                                    user_id uuid NOT NULL,
                                    reason character varying(255),
                                    source character varying(255) NOT NULL,
                                    stripe_charge_id character varying(255),
                                    CONSTRAINT fraud_flags_source_check CHECK (((source)::text = ANY ((ARRAY['STRIPE'::character varying, 'MANUAL'::character varying, 'SYSTEM'::character varying])::text[])))
);


--
-- Name: fraud_risk_assessments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fraud_risk_assessments (
                                               score integer NOT NULL,
                                               created_at timestamp(6) with time zone NOT NULL,
                                               id uuid NOT NULL,
                                               user_id uuid NOT NULL,
                                               reasons text,
                                               risk_level character varying(255) NOT NULL,
                                               CONSTRAINT fraud_risk_assessments_risk_level_check CHECK (((risk_level)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[])))
);


--
-- Name: fraud_risk_scores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fraud_risk_scores (
                                          score integer NOT NULL,
                                          evaluated_at timestamp(6) with time zone NOT NULL,
                                          user_id bigint NOT NULL,
                                          factors jsonb
);


--
-- Name: fraud_scores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fraud_scores (
                                     score integer NOT NULL,
                                     calculated_at timestamp(6) with time zone NOT NULL,
                                     id bigint NOT NULL,
                                     user_id bigint NOT NULL,
                                     risk_level character varying(255) NOT NULL
);


--
-- Name: fraud_scores_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.fraud_scores_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fraud_scores_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.fraud_scores_id_seq OWNED BY public.fraud_scores.id;


--
-- Name: fraud_signals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fraud_signals (
                                      score integer NOT NULL,
                                      created_at timestamp(6) with time zone NOT NULL,
                                      id uuid NOT NULL,
                                      room_id uuid,
                                      user_id uuid NOT NULL,
                                      reasons text,
                                      risk_level character varying(255) NOT NULL,
                                      CONSTRAINT fraud_signals_risk_level_check CHECK (((risk_level)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[])))
);


--
-- Name: highlighted_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.highlighted_messages (
                                             amount numeric(38,2) NOT NULL,
                                             moderated boolean NOT NULL,
                                             created_at timestamp(6) with time zone NOT NULL,
                                             moderated_at timestamp(6) with time zone,
                                             moderated_by bigint,
                                             user_id bigint NOT NULL,
                                             id uuid NOT NULL,
                                             room_id uuid NOT NULL,
                                             client_request_id character varying(255),
                                             content character varying(255) NOT NULL,
                                             currency character varying(255) NOT NULL,
                                             highlight_type character varying(255) NOT NULL,
                                             message_id character varying(255) NOT NULL,
                                             moderation_reason character varying(255),
                                             status character varying(255) NOT NULL,
                                             stripe_payment_intent_id character varying(255),
                                             CONSTRAINT highlighted_messages_highlight_type_check CHECK (((highlight_type)::text = ANY ((ARRAY['COLOR'::character varying, 'PINNED'::character varying, 'LARGE'::character varying])::text[]))),
    CONSTRAINT highlighted_messages_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PENDING_REVIEW'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoices (
                                 gross_amount numeric(38,2) NOT NULL,
                                 net_amount numeric(38,2) NOT NULL,
                                 vat_amount numeric(38,2) NOT NULL,
                                 issued_at timestamp(6) with time zone NOT NULL,
                                 user_id bigint NOT NULL,
                                 id uuid NOT NULL,
                                 billing_address character varying(255),
                                 billing_email character varying(255),
                                 billing_name character varying(255),
                                 country_code character varying(255) NOT NULL,
                                 currency character varying(255) NOT NULL,
                                 invoice_number character varying(255) NOT NULL,
                                 invoice_type character varying(255) NOT NULL,
                                 seller_address character varying(255),
                                 seller_email character varying(255),
                                 seller_name character varying(255),
                                 seller_vat_number character varying(255),
                                 status character varying(255) NOT NULL,
                                 stripe_invoice_id character varying(255),
                                 CONSTRAINT invoices_invoice_type_check CHECK (((invoice_type)::text = ANY ((ARRAY['SUBSCRIPTION'::character varying, 'PPV'::character varying, 'TOKENS'::character varying, 'TIPS'::character varying])::text[]))),
    CONSTRAINT invoices_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PAID'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: leaderboard_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leaderboard_entries (
                                            leaderboard_rank integer NOT NULL,
                                            reference_date date NOT NULL,
                                            total_earnings numeric(19,2) NOT NULL,
                                            calculated_at timestamp(6) with time zone NOT NULL,
                                            total_subscribers bigint NOT NULL,
                                            total_viewers bigint NOT NULL,
                                            creator_id uuid NOT NULL,
                                            id uuid NOT NULL,
                                            category character varying(255),
                                            period character varying(255) NOT NULL,
                                            CONSTRAINT leaderboard_entries_period_check CHECK (((period)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'MONTHLY'::character varying])::text[])))
);


--
-- Name: legacy_chargebacks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.legacy_chargebacks (
                                           amount numeric(38,2),
                                           resolved boolean,
                                           created_at timestamp(6) with time zone NOT NULL,
                                           creator_id bigint,
                                           updated_at timestamp(6) with time zone,
                                           id uuid NOT NULL,
                                           transaction_id uuid NOT NULL,
                                           user_id uuid NOT NULL,
                                           currency character varying(255),
                                           device_fingerprint character varying(255),
                                           ip_address character varying(255),
                                           payment_method_brand character varying(255),
                                           payment_method_fingerprint character varying(255),
                                           payment_method_last4 character varying(255),
                                           reason character varying(255),
                                           status character varying(255) NOT NULL,
                                           stripe_charge_id character varying(255) NOT NULL,
                                           stripe_dispute_id character varying(255),
                                           CONSTRAINT legacy_chargebacks_status_check CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'UNDER_REVIEW'::character varying, 'WON'::character varying, 'LOST'::character varying])::text[])))
);


--
-- Name: legacy_creator_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.legacy_creator_profiles (
                                                active boolean NOT NULL,
                                                created_at timestamp(6) with time zone NOT NULL,
                                                updated_at timestamp(6) with time zone NOT NULL,
                                                user_id bigint NOT NULL,
                                                id uuid NOT NULL,
                                                avatar_url character varying(512),
                                                banner_url character varying(255),
                                                bio text,
                                                category character varying(255),
                                                display_name character varying(255) NOT NULL,
                                                username character varying(255) NOT NULL
);


--
-- Name: legacy_creator_stripe_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.legacy_creator_stripe_accounts (
                                                       charges_enabled boolean NOT NULL,
                                                       onboarding_completed boolean NOT NULL,
                                                       payouts_enabled boolean NOT NULL,
                                                       created_at timestamp(6) with time zone NOT NULL,
                                                       creator_id bigint NOT NULL,
                                                       updated_at timestamp(6) with time zone NOT NULL,
                                                       id uuid NOT NULL,
                                                       onboarding_status character varying(255) NOT NULL,
                                                       stripe_account_id character varying(255) NOT NULL,
                                                       CONSTRAINT legacy_creator_stripe_accounts_onboarding_status_check CHECK (((onboarding_status)::text = ANY ((ARRAY['NOT_STARTED'::character varying, 'PENDING'::character varying, 'VERIFIED'::character varying])::text[])))
);


--
-- Name: live_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.live_access (
                                    created_at timestamp(6) with time zone NOT NULL,
                                    creator_user_id bigint NOT NULL,
                                    expires_at timestamp(6) with time zone NOT NULL,
                                    id bigint NOT NULL,
                                    viewer_user_id bigint NOT NULL
);


--
-- Name: live_access_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.live_access_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: live_access_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.live_access_id_seq OWNED BY public.live_access.id;


--
-- Name: live_streams; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.live_streams (
                                     is_live boolean NOT NULL,
                                     is_premium boolean NOT NULL,
                                     recording_enabled boolean NOT NULL,
                                     creator_id bigint NOT NULL,
                                     ended_at timestamp(6) with time zone,
                                     started_at timestamp(6) with time zone NOT NULL,
                                     id uuid NOT NULL,
                                     ppv_content_id uuid,
                                     hls_url character varying(255),
                                     recording_path character varying(255),
                                     stream_key character varying(255) NOT NULL,
                                     title character varying(255) NOT NULL
);


--
-- Name: live_streams_v2; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.live_streams_v2 (
                                        created_at timestamp(6) with time zone NOT NULL,
                                        creator_id bigint NOT NULL,
                                        ended_at timestamp(6) with time zone,
                                        started_at timestamp(6) with time zone,
                                        id uuid NOT NULL,
                                        status character varying(255) NOT NULL,
                                        CONSTRAINT live_streams_v2_status_check CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'LIVE'::character varying, 'PAUSED'::character varying, 'ENDED'::character varying])::text[])))
);


--
-- Name: livestream_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.livestream_sessions (
                                            admission_price numeric(10,2) NOT NULL,
                                            is_paid boolean NOT NULL,
                                            viewer_count integer NOT NULL,
                                            creator_id bigint NOT NULL,
                                            ended_at timestamp(6) without time zone,
                                            id bigint NOT NULL,
                                            started_at timestamp(6) without time zone,
                                            version bigint,
                                            status character varying(255) NOT NULL,
                                            CONSTRAINT livestream_sessions_status_check CHECK (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'LIVE'::character varying, 'ENDED'::character varying])::text[])))
);


--
-- Name: livestream_sessions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.livestream_sessions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: livestream_sessions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.livestream_sessions_id_seq OWNED BY public.livestream_sessions.id;


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
                                 amount numeric(38,2),
                                 success boolean NOT NULL,
                                 created_at timestamp(6) with time zone NOT NULL,
                                 creator_id bigint,
                                 user_id bigint NOT NULL,
                                 id uuid NOT NULL,
                                 country character varying(255),
                                 currency character varying(255),
                                 device_fingerprint character varying(255),
                                 failure_reason character varying(255),
                                 ip_address character varying(255),
                                 payment_method_brand character varying(255),
                                 payment_method_fingerprint character varying(255),
                                 payment_method_last4 character varying(255),
                                 receipt_url character varying(255),
                                 risk_level character varying(255),
                                 status character varying(255),
                                 stripe_invoice_id character varying(255),
                                 stripe_payment_intent_id character varying(255),
                                 stripe_session_id character varying(255),
                                 user_agent character varying(255),
                                 CONSTRAINT payments_risk_level_check CHECK (((risk_level)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT payments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: payout_audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout_audit_logs (
                                          created_at timestamp(6) with time zone NOT NULL,
                                          actor_id uuid,
                                          id uuid NOT NULL,
                                          payout_id uuid NOT NULL,
                                          message character varying(1000),
                                          action character varying(255) NOT NULL,
                                          actor_type character varying(255) NOT NULL,
                                          new_status character varying(255),
                                          previous_status character varying(255),
                                          CONSTRAINT payout_audit_logs_actor_type_check CHECK (((actor_type)::text = ANY ((ARRAY['SYSTEM'::character varying, 'ADMIN'::character varying, 'STRIPE'::character varying])::text[]))),
    CONSTRAINT payout_audit_logs_new_status_check CHECK (((new_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT payout_audit_logs_previous_status_check CHECK (((previous_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: payout_freeze_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout_freeze_audit_log (
                                                admin_id bigint,
                                                created_at timestamp(6) with time zone NOT NULL,
                                                creator_id bigint NOT NULL,
                                                id bigint NOT NULL,
                                                action character varying(255) NOT NULL,
                                                reason character varying(255)
);


--
-- Name: payout_freeze_audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payout_freeze_audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payout_freeze_audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payout_freeze_audit_log_id_seq OWNED BY public.payout_freeze_audit_log.id;


--
-- Name: payout_freeze_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout_freeze_history (
                                              created_at timestamp(6) with time zone NOT NULL,
                                              creator_id uuid NOT NULL,
                                              id uuid NOT NULL,
                                              reason character varying(255) NOT NULL,
                                              triggered_by character varying(255) NOT NULL
);


--
-- Name: payout_freezes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout_freezes (
                                       active boolean NOT NULL,
                                       created_at timestamp(6) with time zone NOT NULL,
                                       created_by_admin_id bigint,
                                       creator_id bigint NOT NULL,
                                       id bigint NOT NULL,
                                       reason character varying(255)
);


--
-- Name: payout_freezes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payout_freezes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payout_freezes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payout_freezes_id_seq OWNED BY public.payout_freezes.id;


--
-- Name: payout_hold_audits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout_hold_audits (
                                           new_hold_days integer NOT NULL,
                                           prev_hold_days integer,
                                           admin_id bigint,
                                           created_at timestamp(6) with time zone NOT NULL,
                                           new_expires_at timestamp(6) with time zone,
                                           prev_expires_at timestamp(6) with time zone,
                                           id uuid NOT NULL,
                                           subject_id uuid NOT NULL,
                                           action character varying(255) NOT NULL,
                                           new_hold_level character varying(255) NOT NULL,
                                           prev_hold_level character varying(255),
                                           reason text,
                                           subject_type character varying(255) NOT NULL,
                                           type character varying(255) NOT NULL,
                                           CONSTRAINT payout_hold_audits_new_hold_level_check CHECK (((new_hold_level)::text = ANY ((ARRAY['NONE'::character varying, 'SHORT'::character varying, 'MEDIUM'::character varying, 'LONG'::character varying])::text[]))),
    CONSTRAINT payout_hold_audits_prev_hold_level_check CHECK (((prev_hold_level)::text = ANY ((ARRAY['NONE'::character varying, 'SHORT'::character varying, 'MEDIUM'::character varying, 'LONG'::character varying])::text[]))),
    CONSTRAINT payout_hold_audits_subject_type_check CHECK (((subject_type)::text = ANY ((ARRAY['CREATOR'::character varying, 'USER'::character varying, 'TRANSACTION'::character varying])::text[]))),
    CONSTRAINT payout_hold_audits_type_check CHECK (((type)::text = ANY ((ARRAY['SYSTEM'::character varying, 'ADMIN'::character varying, 'STRIPE'::character varying])::text[])))
);


--
-- Name: payout_hold_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout_hold_policies (
                                             hold_days integer NOT NULL,
                                             created_at timestamp(6) with time zone NOT NULL,
                                             expires_at timestamp(6) with time zone,
                                             id uuid NOT NULL,
                                             subject_id uuid NOT NULL,
                                             transaction_id uuid,
                                             hold_level character varying(255) NOT NULL,
                                             reason character varying(255),
                                             risk_level character varying(255),
                                             subject_type character varying(255) NOT NULL,
                                             CONSTRAINT payout_hold_policies_hold_level_check CHECK (((hold_level)::text = ANY ((ARRAY['NONE'::character varying, 'SHORT'::character varying, 'MEDIUM'::character varying, 'LONG'::character varying])::text[]))),
    CONSTRAINT payout_hold_policies_risk_level_check CHECK (((risk_level)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT payout_hold_policies_subject_type_check CHECK (((subject_type)::text = ANY ((ARRAY['CREATOR'::character varying, 'USER'::character varying, 'TRANSACTION'::character varying])::text[])))
);


--
-- Name: payout_holds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout_holds (
                                     created_at timestamp(6) with time zone NOT NULL,
                                     hold_until timestamp(6) with time zone NOT NULL,
                                     id uuid NOT NULL,
                                     transaction_id uuid,
                                     user_id uuid NOT NULL,
                                     reason text,
                                     risk_level character varying(255) NOT NULL,
                                     status character varying(255) NOT NULL,
                                     CONSTRAINT payout_holds_risk_level_check CHECK (((risk_level)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT payout_holds_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'RELEASED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: payout_policy_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout_policy_decisions (
                                                applied_limit_amount numeric(19,2),
                                                risk_score integer,
                                                created_at timestamp(6) with time zone NOT NULL,
                                                creator_id uuid NOT NULL,
                                                explanation_id uuid,
                                                id uuid NOT NULL,
                                                applied_limit_frequency character varying(255) NOT NULL,
                                                decision_source character varying(255) NOT NULL,
                                                reason text,
                                                CONSTRAINT payout_policy_decisions_applied_limit_frequency_check CHECK (((applied_limit_frequency)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying, 'PAUSED'::character varying, 'NO_LIMIT'::character varying])::text[]))),
    CONSTRAINT payout_policy_decisions_decision_source_check CHECK (((decision_source)::text = ANY ((ARRAY['AUTO'::character varying, 'ADMIN'::character varying])::text[])))
);


--
-- Name: payout_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout_requests (
                                        amount numeric(12,2) NOT NULL,
                                        created_at timestamp(6) with time zone NOT NULL,
                                        updated_at timestamp(6) with time zone,
                                        creator_id uuid NOT NULL,
                                        id uuid NOT NULL,
                                        currency character varying(255) NOT NULL,
                                        rejection_reason character varying(255),
                                        status character varying(255) NOT NULL,
                                        stripe_transfer_id character varying(255),
                                        CONSTRAINT payout_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: payout_risks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payout_risks (
                                     risk_score integer NOT NULL,
                                     last_evaluated_at timestamp(6) with time zone,
                                     user_id bigint NOT NULL,
                                     id uuid NOT NULL,
                                     reasons text
);


--
-- Name: payouts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payouts (
                                eur_amount numeric(38,2) NOT NULL,
                                created_at timestamp(6) with time zone NOT NULL,
                                token_amount bigint NOT NULL,
                                updated_at timestamp(6) with time zone NOT NULL,
                                user_id bigint NOT NULL,
                                id uuid NOT NULL,
                                error_message character varying(255),
                                status character varying(255) NOT NULL,
                                stripe_transfer_id character varying(255),
                                CONSTRAINT payouts_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: platform_analytics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_analytics (
                                           date date NOT NULL,
                                           total_revenue numeric(19,2) NOT NULL,
                                           active_subscriptions bigint NOT NULL,
                                           churned_subscriptions bigint NOT NULL,
                                           new_subscriptions bigint NOT NULL,
                                           registrations bigint NOT NULL,
                                           unique_visits bigint NOT NULL
);


--
-- Name: platform_balances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.platform_balances (
                                          available_balance numeric(38,2) NOT NULL,
                                          total_creator_earnings numeric(38,2) NOT NULL,
                                          total_fees_collected numeric(38,2) NOT NULL,
                                          updated_at timestamp(6) with time zone NOT NULL,
                                          id uuid NOT NULL
);


--
-- Name: post_likes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.post_likes (
                                   created_at timestamp(6) with time zone NOT NULL,
                                   user_id bigint NOT NULL,
                                   id uuid NOT NULL,
                                   post_id uuid NOT NULL
);


--
-- Name: ppv_chat_access; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ppv_chat_access (
                                        expires_at timestamp(6) with time zone,
                                        granted_at timestamp(6) with time zone NOT NULL,
                                        user_id bigint NOT NULL,
                                        id uuid NOT NULL,
                                        ppv_content_id uuid NOT NULL,
                                        room_id uuid NOT NULL
);


--
-- Name: ppv_content; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ppv_content (
                                    active boolean NOT NULL,
                                    price numeric(38,2) NOT NULL,
                                    created_at timestamp(6) with time zone NOT NULL,
                                    creator_id bigint NOT NULL,
                                    id uuid NOT NULL,
                                    content_url character varying(255) NOT NULL,
                                    currency character varying(255) NOT NULL,
                                    description text,
                                    title character varying(255) NOT NULL
);


--
-- Name: ppv_purchases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ppv_purchases (
                                      amount numeric(38,2) NOT NULL,
                                      purchased_at timestamp(6) with time zone NOT NULL,
                                      user_id bigint NOT NULL,
                                      id uuid NOT NULL,
                                      ppv_content_id uuid NOT NULL,
                                      client_request_id character varying(255),
                                      status character varying(255) NOT NULL,
                                      stripe_payment_intent_id character varying(255),
                                      CONSTRAINT ppv_purchases_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'FAILED'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: private_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.private_sessions (
                                         accepted_at timestamp(6) with time zone,
                                         creator_id bigint NOT NULL,
                                         ended_at timestamp(6) with time zone,
                                         last_billed_at timestamp(6) with time zone,
                                         price_per_minute bigint NOT NULL,
                                         rejected_at timestamp(6) with time zone,
                                         requested_at timestamp(6) with time zone,
                                         started_at timestamp(6) with time zone,
                                         viewer_id bigint NOT NULL,
                                         id uuid NOT NULL,
                                         end_reason character varying(255),
                                         status character varying(255) NOT NULL,
                                         CONSTRAINT private_sessions_status_check CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying, 'ACTIVE'::character varying, 'ENDED'::character varying])::text[])))
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
                                       revoked boolean NOT NULL,
                                       expiry_date timestamp(6) with time zone NOT NULL,
                                       user_id bigint NOT NULL,
                                       id uuid NOT NULL,
                                       replaced_by_token character varying(255),
                                       token character varying(255) NOT NULL
);


--
-- Name: reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reports (
                                created_at timestamp(6) with time zone NOT NULL,
                                reported_user_id bigint NOT NULL,
                                reporter_user_id bigint,
                                updated_at timestamp(6) with time zone,
                                id uuid NOT NULL,
                                stream_id uuid,
                                description text,
                                reason character varying(255) NOT NULL,
                                status character varying(255) NOT NULL,
                                CONSTRAINT reports_reason_check CHECK (((reason)::text = ANY ((ARRAY['UNDERAGE'::character varying, 'COPYRIGHT'::character varying, 'HARASSMENT'::character varying, 'VIOLENCE'::character varying, 'NON_CONSENSUAL'::character varying, 'SPAM'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT reports_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'REVIEWED'::character varying, 'RESOLVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: reputation_change_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reputation_change_logs (
                                               new_score integer NOT NULL,
                                               old_score integer NOT NULL,
                                               created_at timestamp(6) with time zone NOT NULL,
                                               creator_id uuid NOT NULL,
                                               id uuid NOT NULL,
                                               reason character varying(255) NOT NULL,
                                               source character varying(255) NOT NULL,
                                               CONSTRAINT reputation_change_logs_source_check CHECK (((source)::text = ANY ((ARRAY['SYSTEM'::character varying, 'ADMIN'::character varying, 'AI'::character varying])::text[])))
);


--
-- Name: reputation_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reputation_events (
                                          delta_score integer NOT NULL,
                                          created_at timestamp(6) with time zone NOT NULL,
                                          creator_id uuid NOT NULL,
                                          id uuid NOT NULL,
                                          source character varying(255) NOT NULL,
                                          type character varying(255) NOT NULL,
                                          metadata jsonb,
                                          CONSTRAINT reputation_events_delta_score_check CHECK (((delta_score <= 100) AND (delta_score >= '-100'::integer))),
                                          CONSTRAINT reputation_events_source_check CHECK (((source)::text = ANY ((ARRAY['SYSTEM'::character varying, 'ADMIN'::character varying, 'AI'::character varying])::text[]))),
    CONSTRAINT reputation_events_type_check CHECK (((type)::text = ANY ((ARRAY['TIP'::character varying, 'CHARGEBACK'::character varying, 'REPORT'::character varying, 'MANUAL_ADJUSTMENT'::character varying, 'FRAUD_FLAG'::character varying, 'DECAY'::character varying, 'RECOVERY'::character varying])::text[])))
);


--
-- Name: risk_decision_audits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.risk_decision_audits (
                                             score integer,
                                             created_at timestamp(6) with time zone NOT NULL,
                                             id uuid NOT NULL,
                                             transaction_id uuid,
                                             user_id uuid NOT NULL,
                                             actions_taken text,
                                             decision_type character varying(255) NOT NULL,
                                             metadata text,
                                             reason text,
                                             risk_level character varying(255) NOT NULL,
                                             triggered_by character varying(255) NOT NULL,
                                             CONSTRAINT risk_decision_audits_risk_level_check CHECK (((risk_level)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[])))
);


--
-- Name: risk_explanation_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.risk_explanation_logs (
                                              "timestamp" timestamp(6) with time zone NOT NULL,
                                              explanation_id uuid NOT NULL,
                                              id uuid NOT NULL,
                                              requester_id uuid NOT NULL,
                                              role character varying(255) NOT NULL,
                                              CONSTRAINT risk_explanation_logs_role_check CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'PREMIUM'::character varying, 'ADMIN'::character varying, 'MODERATOR'::character varying, 'CREATOR'::character varying])::text[])))
);


--
-- Name: risk_explanations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.risk_explanations (
                                          risk_score integer NOT NULL,
                                          generated_at timestamp(6) with time zone NOT NULL,
                                          id uuid NOT NULL,
                                          subject_id uuid NOT NULL,
                                          decision character varying(255) NOT NULL,
                                          explanation_text text,
                                          subject_type character varying(255) NOT NULL,
                                          factors jsonb,
                                          CONSTRAINT risk_explanations_decision_check CHECK (((decision)::text = ANY ((ARRAY['ALLOW'::character varying, 'LIMIT'::character varying, 'REVIEW'::character varying, 'BLOCK'::character varying])::text[]))),
    CONSTRAINT risk_explanations_subject_type_check CHECK (((subject_type)::text = ANY ((ARRAY['CREATOR'::character varying, 'USER'::character varying, 'TRANSACTION'::character varying])::text[])))
);


--
-- Name: risk_scores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.risk_scores (
                                    score integer NOT NULL,
                                    last_evaluated_at timestamp(6) with time zone,
                                    user_id uuid NOT NULL,
                                    breakdown text,
                                    CONSTRAINT risk_scores_score_check CHECK (((score <= 100) AND (score >= 0)))
);


--
-- Name: rule_fraud_signals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rule_fraud_signals (
                                           resolved boolean NOT NULL,
                                           created_at timestamp(6) with time zone NOT NULL,
                                           resolved_at timestamp(6) with time zone,
                                           user_id bigint NOT NULL,
                                           id uuid NOT NULL,
                                           resolved_by uuid,
                                           action_reason character varying(255),
                                           reason character varying(255) NOT NULL,
                                           risk_level character varying(255) NOT NULL,
                                           source character varying(255) NOT NULL,
                                           type character varying(255) NOT NULL,
                                           CONSTRAINT rule_fraud_signals_risk_level_check CHECK (((risk_level)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying])::text[]))),
    CONSTRAINT rule_fraud_signals_source_check CHECK (((source)::text = ANY ((ARRAY['PAYMENT'::character varying, 'LOGIN'::character varying, 'SYSTEM'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT rule_fraud_signals_type_check CHECK (((type)::text = ANY ((ARRAY['NEW_DEVICE'::character varying, 'NEW_IP'::character varying, 'VPN_PROXY'::character varying, 'TOR_EXIT'::character varying, 'DEVICE_MISMATCH'::character varying, 'VELOCITY_WARNING'::character varying, 'CHAT_SPAM'::character varying, 'PAYMENT_FAILURE'::character varying, 'CHARGEBACK'::character varying, 'COUNTRY_MISMATCH'::character varying, 'IP_MISMATCH'::character varying, 'AUTOMATIC_ESCALATION'::character varying, 'FRAUD_COOLDOWN'::character varying, 'ADMIN_OVERRIDE'::character varying, 'ADMIN_UNBLOCK'::character varying, 'TRUST_EVALUATION_BLOCK'::character varying, 'CHARGEBACK_CORRELATION'::character varying, 'AML_HIGH_RISK'::character varying, 'COLLUSION_DETECTED'::character varying])::text[])))
);


--
-- Name: slow_mode_bypass; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.slow_mode_bypass (
                                         created_at timestamp(6) with time zone NOT NULL,
                                         expires_at timestamp(6) with time zone NOT NULL,
                                         user_id bigint NOT NULL,
                                         id uuid NOT NULL,
                                         room_id uuid NOT NULL,
                                         source character varying(255) NOT NULL,
                                         CONSTRAINT slow_mode_bypass_source_check CHECK (((source)::text = ANY ((ARRAY['SUPERTIP'::character varying, 'PPV'::character varying, 'MANUAL'::character varying])::text[])))
);


--
-- Name: stream_rooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stream_rooms (
                                     admission_price numeric(10,2) NOT NULL,
                                     is_paid boolean NOT NULL,
                                     is_premium boolean NOT NULL,
                                     slow_mode boolean NOT NULL,
                                     viewer_count integer NOT NULL,
                                     created_at timestamp(6) with time zone NOT NULL,
                                     creator_id bigint NOT NULL,
                                     ended_at timestamp(6) with time zone,
                                     min_chat_tokens bigint,
                                     price_per_message bigint,
                                     started_at timestamp(6) with time zone,
                                     id uuid NOT NULL,
                                     description character varying(255),
                                     stream_title character varying(255)
);


--
-- Name: stripe_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stripe_accounts (
                                        charges_enabled boolean NOT NULL,
                                        onboarding_completed boolean NOT NULL,
                                        payouts_enabled boolean NOT NULL,
                                        created_at timestamp(6) with time zone NOT NULL,
                                        updated_at timestamp(6) with time zone NOT NULL,
                                        user_id bigint NOT NULL,
                                        id uuid NOT NULL,
                                        stripe_account_id character varying(255)
);


--
-- Name: stripe_webhook_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stripe_webhook_events (
                                              received_at timestamp(6) with time zone NOT NULL,
                                              event_id character varying(255) NOT NULL,
                                              payload_hash character varying(255),
                                              type character varying(255) NOT NULL
);


--
-- Name: subscriptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscriptions (
                                      cancel_at_period_end boolean NOT NULL,
                                      created_at timestamp(6) with time zone NOT NULL,
                                      current_period_end timestamp(6) with time zone,
                                      current_period_start timestamp(6) with time zone,
                                      next_invoice_date timestamp(6) with time zone,
                                      updated_at timestamp(6) with time zone NOT NULL,
                                      user_id bigint NOT NULL,
                                      id uuid NOT NULL,
                                      last4 character varying(255),
                                      payment_method_brand character varying(255),
                                      status character varying(255) NOT NULL,
                                      stripe_subscription_id character varying(255),
                                      CONSTRAINT subscriptions_status_check CHECK (((status)::text = ANY ((ARRAY['NONE'::character varying, 'TRIAL'::character varying, 'ACTIVE'::character varying, 'PAST_DUE'::character varying, 'CANCELED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: super_tips; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.super_tips (
                                   amount numeric(38,2) NOT NULL,
                                   duration_seconds integer NOT NULL,
                                   created_at timestamp(6) with time zone NOT NULL,
                                   creator_id bigint NOT NULL,
                                   from_user_id bigint NOT NULL,
                                   id uuid NOT NULL,
                                   room_id uuid NOT NULL,
                                   client_request_id character varying(255),
                                   highlight_level character varying(255) NOT NULL,
                                   message character varying(255),
                                   status character varying(255) NOT NULL,
                                   CONSTRAINT super_tips_highlight_level_check CHECK (((highlight_level)::text = ANY ((ARRAY['BASIC'::character varying, 'PREMIUM'::character varying, 'ULTRA'::character varying])::text[]))),
    CONSTRAINT super_tips_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PENDING_REVIEW'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: tip_actions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tip_actions (
                                    is_enabled boolean NOT NULL,
                                    amount bigint NOT NULL,
                                    creator_id bigint NOT NULL,
                                    id uuid NOT NULL,
                                    description character varying(255) NOT NULL
);


--
-- Name: tip_goals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tip_goals (
                                  auto_reset boolean NOT NULL,
                                  is_active boolean NOT NULL,
                                  order_index integer,
                                  created_at timestamp(6) with time zone,
                                  creator_id bigint NOT NULL,
                                  current_amount bigint NOT NULL,
                                  target_amount bigint NOT NULL,
                                  updated_at timestamp(6) with time zone,
                                  id uuid NOT NULL,
                                  title character varying(255)
);


--
-- Name: tip_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tip_records (
                                    amount bigint NOT NULL,
                                    created_at timestamp(6) with time zone NOT NULL,
                                    creator_earning_tokens bigint NOT NULL,
                                    creator_id bigint NOT NULL,
                                    platform_fee_tokens bigint NOT NULL,
                                    viewer_id bigint NOT NULL,
                                    id uuid NOT NULL,
                                    room_id uuid
);


--
-- Name: tips; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tips (
                             amount numeric(38,2) NOT NULL,
                             created_at timestamp(6) with time zone NOT NULL,
                             creator_id bigint NOT NULL,
                             from_user_id bigint NOT NULL,
                             id uuid NOT NULL,
                             room_id uuid,
                             client_request_id character varying(255),
                             currency character varying(255) NOT NULL,
                             message character varying(255),
                             status character varying(255) NOT NULL,
                             stripe_payment_intent_id character varying(255),
                             CONSTRAINT tips_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PENDING_REVIEW'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'REFUNDED'::character varying])::text[])))
);


--
-- Name: token_packages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.token_packages (
                                       active boolean NOT NULL,
                                       price numeric(38,2) NOT NULL,
                                       token_amount bigint NOT NULL,
                                       id uuid NOT NULL,
                                       currency character varying(255) NOT NULL,
                                       name character varying(255) NOT NULL,
                                       stripe_price_id character varying(255) NOT NULL
);


--
-- Name: token_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.token_transactions (
                                           amount bigint NOT NULL,
                                           created_at timestamp(6) with time zone NOT NULL,
                                           creator_id bigint NOT NULL,
                                           user_id bigint NOT NULL,
                                           id uuid NOT NULL,
                                           type character varying(255) NOT NULL,
                                           CONSTRAINT token_transactions_type_check CHECK (((type)::text = 'STREAM_UNLOCK'::text))
);


--
-- Name: user_badges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_badges (
                                    created_at timestamp(6) with time zone,
                                    expires_at timestamp(6) with time zone,
                                    user_id bigint NOT NULL,
                                    badge_id uuid NOT NULL,
                                    id uuid NOT NULL
);


--
-- Name: user_restrictions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_restrictions (
                                          created_at timestamp(6) with time zone NOT NULL,
                                          expires_at timestamp(6) with time zone,
                                          id uuid NOT NULL,
                                          user_id uuid NOT NULL,
                                          reason character varying(255) NOT NULL,
                                          restriction_level character varying(255) NOT NULL,
                                          CONSTRAINT user_restrictions_restriction_level_check CHECK (((restriction_level)::text = ANY ((ARRAY['NONE'::character varying, 'SLOW_MODE'::character varying, 'TIP_COOLDOWN'::character varying, 'TIP_LIMIT'::character varying, 'CHAT_MUTE'::character varying, 'FRAUD_LOCK'::character varying, 'TEMP_SUSPENSION'::character varying])::text[])))
);


--
-- Name: user_risk_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_risk_state (
                                        payment_locked boolean NOT NULL,
                                        blocked_until timestamp(6) with time zone,
                                        updated_at timestamp(6) with time zone NOT NULL,
                                        user_id bigint NOT NULL,
                                        current_risk character varying(255) NOT NULL,
                                        CONSTRAINT user_risk_state_current_risk_check CHECK (((current_risk)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying])::text[])))
);


--
-- Name: user_wallets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_wallets (
                                     balance bigint NOT NULL,
                                     reserved_balance bigint NOT NULL,
                                     updated_at timestamp(6) with time zone NOT NULL,
                                     user_id bigint NOT NULL,
                                     id uuid NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
                              email_verified boolean NOT NULL,
                              failed_login_attempts integer,
                              payouts_enabled boolean NOT NULL,
                              shadowbanned boolean NOT NULL,
                              stripe_onboarding_complete boolean NOT NULL,
                              trust_score integer NOT NULL,
                              created_at timestamp(6) with time zone NOT NULL,
                              id bigint NOT NULL,
                              lockout_until timestamp(6) with time zone,
                              sessions_invalidated_at timestamp(6) with time zone,
                              display_name character varying(255),
                              email character varying(255) NOT NULL,
                              email_verification_token character varying(255),
                              fraud_risk_level character varying(255) NOT NULL,
                              password character varying(255) NOT NULL,
                              role character varying(255) NOT NULL,
                              status character varying(255) NOT NULL,
                              stripe_account_id character varying(255),
                              CONSTRAINT users_fraud_risk_level_check CHECK (((fraud_risk_level)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying])::text[]))),
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'PREMIUM'::character varying, 'ADMIN'::character varying, 'MODERATOR'::character varying, 'CREATOR'::character varying])::text[]))),
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'FLAGGED'::character varying, 'PAYOUTS_FROZEN'::character varying, 'SUSPENDED'::character varying, 'MANUAL_REVIEW'::character varying, 'TERMINATED'::character varying])::text[])))
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: velocity_metrics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.velocity_metrics (
                                         count integer NOT NULL,
                                         user_id bigint NOT NULL,
                                         window_end timestamp(6) with time zone NOT NULL,
                                         window_start timestamp(6) with time zone NOT NULL,
                                         id uuid NOT NULL,
                                         action_type character varying(255) NOT NULL,
                                         CONSTRAINT velocity_metrics_action_type_check CHECK (((action_type)::text = ANY ((ARRAY['LOGIN'::character varying, 'TIP'::character varying, 'PAYMENT'::character varying, 'MESSAGE'::character varying])::text[])))
);


--
-- Name: wallet_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wallet_transactions (
                                            amount bigint NOT NULL,
                                            created_at timestamp(6) with time zone NOT NULL,
                                            user_id bigint NOT NULL,
                                            id uuid NOT NULL,
                                            reason character varying(255) NOT NULL,
                                            reference_id character varying(255),
                                            type character varying(255) NOT NULL,
                                            CONSTRAINT wallet_transactions_type_check CHECK (((type)::text = ANY ((ARRAY['PURCHASE'::character varying, 'TIP'::character varying, 'CHAT'::character varying, 'BADGE'::character varying, 'PRIVATE_SHOW'::character varying, 'LIVESTREAM_ADMISSION'::character varying])::text[])))
);


--
-- Name: webhook_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.webhook_events (
                                       processed boolean NOT NULL,
                                       created_at timestamp(6) with time zone NOT NULL,
                                       id uuid NOT NULL,
                                       error_message text,
                                       event_type character varying(255),
                                       payload text,
                                       stripe_event_id character varying(255) NOT NULL
);


--
-- Name: weekly_tip_leaderboards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.weekly_tip_leaderboards (
                                                total_amount numeric(19,2) NOT NULL,
                                                week_number integer NOT NULL,
                                                year integer NOT NULL,
                                                creator_id bigint NOT NULL,
                                                id uuid NOT NULL,
                                                username character varying(255) NOT NULL
);


--
-- Name: abuse_reports id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abuse_reports ALTER COLUMN id SET DEFAULT nextval('public.abuse_reports_id_seq'::regclass);


--
-- Name: chargebacks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chargebacks ALTER COLUMN id SET DEFAULT nextval('public.chargebacks_id_seq'::regclass);


--
-- Name: chat_messages id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_messages ALTER COLUMN id SET DEFAULT nextval('public.chat_messages_id_seq'::regclass);


--
-- Name: chat_moderations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_moderations ALTER COLUMN id SET DEFAULT nextval('public.chat_moderations_id_seq'::regclass);


--
-- Name: chat_rooms_v2 id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_rooms_v2 ALTER COLUMN id SET DEFAULT nextval('public.chat_rooms_v2_id_seq'::regclass);


--
-- Name: chat_violation_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_violation_logs ALTER COLUMN id SET DEFAULT nextval('public.chat_violation_logs_id_seq'::regclass);


--
-- Name: creator_follow id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_follow ALTER COLUMN id SET DEFAULT nextval('public.creator_follow_id_seq'::regclass);


--
-- Name: creator_monetization id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_monetization ALTER COLUMN id SET DEFAULT nextval('public.creator_monetization_id_seq'::regclass);


--
-- Name: creator_presence id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_presence ALTER COLUMN id SET DEFAULT nextval('public.creator_presence_id_seq'::regclass);


--
-- Name: creator_profiles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_profiles ALTER COLUMN id SET DEFAULT nextval('public.creator_profiles_id_seq'::regclass);


--
-- Name: creator_records id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_records ALTER COLUMN id SET DEFAULT nextval('public.creator_records_id_seq'::regclass);


--
-- Name: creator_stripe_accounts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_stripe_accounts ALTER COLUMN id SET DEFAULT nextval('public.creator_stripe_accounts_id_seq'::regclass);


--
-- Name: creator_verification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_verification ALTER COLUMN id SET DEFAULT nextval('public.creator_verification_id_seq'::regclass);


--
-- Name: export_jobs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.export_jobs ALTER COLUMN id SET DEFAULT nextval('public.export_jobs_id_seq'::regclass);


--
-- Name: fraud_scores id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_scores ALTER COLUMN id SET DEFAULT nextval('public.fraud_scores_id_seq'::regclass);


--
-- Name: live_access id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.live_access ALTER COLUMN id SET DEFAULT nextval('public.live_access_id_seq'::regclass);


--
-- Name: livestream_sessions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.livestream_sessions ALTER COLUMN id SET DEFAULT nextval('public.livestream_sessions_id_seq'::regclass);


--
-- Name: payout_freeze_audit_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_freeze_audit_log ALTER COLUMN id SET DEFAULT nextval('public.payout_freeze_audit_log_id_seq'::regclass);


--
-- Name: payout_freezes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_freezes ALTER COLUMN id SET DEFAULT nextval('public.payout_freezes_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: abuse_events abuse_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abuse_events
    ADD CONSTRAINT abuse_events_pkey PRIMARY KEY (id);


--
-- Name: abuse_reports abuse_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abuse_reports
    ADD CONSTRAINT abuse_reports_pkey PRIMARY KEY (id);


--
-- Name: aml_incidents aml_incidents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aml_incidents
    ADD CONSTRAINT aml_incidents_pkey PRIMARY KEY (id);


--
-- Name: aml_risk_scores aml_risk_scores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aml_risk_scores
    ADD CONSTRAINT aml_risk_scores_pkey PRIMARY KEY (id);


--
-- Name: aml_rules aml_rules_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aml_rules
    ADD CONSTRAINT aml_rules_code_key UNIQUE (code);


--
-- Name: aml_rules aml_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.aml_rules
    ADD CONSTRAINT aml_rules_pkey PRIMARY KEY (id);


--
-- Name: analytics_events analytics_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.analytics_events
    ADD CONSTRAINT analytics_events_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: badges badges_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.badges
    ADD CONSTRAINT badges_name_key UNIQUE (name);


--
-- Name: badges badges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.badges
    ADD CONSTRAINT badges_pkey PRIMARY KEY (id);


--
-- Name: chargeback_audits chargeback_audits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chargeback_audits
    ADD CONSTRAINT chargeback_audits_pkey PRIMARY KEY (id);


--
-- Name: chargeback_cases chargeback_cases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chargeback_cases
    ADD CONSTRAINT chargeback_cases_pkey PRIMARY KEY (id);


--
-- Name: chargebacks chargebacks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chargebacks
    ADD CONSTRAINT chargebacks_pkey PRIMARY KEY (id);


--
-- Name: chargebacks chargebacks_stripe_charge_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chargebacks
    ADD CONSTRAINT chargebacks_stripe_charge_id_key UNIQUE (stripe_charge_id);


--
-- Name: chat_messages chat_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT chat_messages_pkey PRIMARY KEY (id);


--
-- Name: chat_moderations chat_moderations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_moderations
    ADD CONSTRAINT chat_moderations_pkey PRIMARY KEY (id);


--
-- Name: chat_rooms chat_rooms_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_rooms
    ADD CONSTRAINT chat_rooms_name_key UNIQUE (name);


--
-- Name: chat_rooms chat_rooms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_rooms
    ADD CONSTRAINT chat_rooms_pkey PRIMARY KEY (id);


--
-- Name: chat_rooms_v2 chat_rooms_v2_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_rooms_v2
    ADD CONSTRAINT chat_rooms_v2_pkey PRIMARY KEY (id);


--
-- Name: chat_violation_logs chat_violation_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_violation_logs
    ADD CONSTRAINT chat_violation_logs_pkey PRIMARY KEY (id);


--
-- Name: content_analytics content_analytics_content_id_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_analytics
    ADD CONSTRAINT content_analytics_content_id_date_key UNIQUE (content_id, date);


--
-- Name: content_analytics content_analytics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_analytics
    ADD CONSTRAINT content_analytics_pkey PRIMARY KEY (id);


--
-- Name: content content_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content
    ADD CONSTRAINT content_pkey PRIMARY KEY (id);


--
-- Name: creator_analytics creator_analytics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_analytics
    ADD CONSTRAINT creator_analytics_pkey PRIMARY KEY (id);


--
-- Name: creator_collusion_records creator_collusion_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_collusion_records
    ADD CONSTRAINT creator_collusion_records_pkey PRIMARY KEY (id);


--
-- Name: creator_earnings_balances creator_earnings_balances_creator_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_balances
    ADD CONSTRAINT creator_earnings_balances_creator_id_key UNIQUE (creator_id);


--
-- Name: creator_earnings_balances creator_earnings_balances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_balances
    ADD CONSTRAINT creator_earnings_balances_pkey PRIMARY KEY (id);


--
-- Name: creator_earnings_history creator_earnings_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_history
    ADD CONSTRAINT creator_earnings_history_pkey PRIMARY KEY (id);


--
-- Name: creator_earnings_invoices creator_earnings_invoices_invoice_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_invoices
    ADD CONSTRAINT creator_earnings_invoices_invoice_number_key UNIQUE (invoice_number);


--
-- Name: creator_earnings_invoices creator_earnings_invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_invoices
    ADD CONSTRAINT creator_earnings_invoices_pkey PRIMARY KEY (id);


--
-- Name: creator_earnings creator_earnings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings
    ADD CONSTRAINT creator_earnings_pkey PRIMARY KEY (id);


--
-- Name: creator_earnings creator_earnings_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings
    ADD CONSTRAINT creator_earnings_user_id_key UNIQUE (user_id);


--
-- Name: creator_follow creator_follow_follower_id_creator_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_follow
    ADD CONSTRAINT creator_follow_follower_id_creator_id_key UNIQUE (follower_id, creator_id);


--
-- Name: creator_follow creator_follow_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_follow
    ADD CONSTRAINT creator_follow_pkey PRIMARY KEY (id);


--
-- Name: creator_moderation_settings creator_moderation_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_moderation_settings
    ADD CONSTRAINT creator_moderation_settings_pkey PRIMARY KEY (creator_user_id);


--
-- Name: creator_monetization creator_monetization_creator_profile_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_monetization
    ADD CONSTRAINT creator_monetization_creator_profile_id_key UNIQUE (creator_profile_id);


--
-- Name: creator_monetization creator_monetization_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_monetization
    ADD CONSTRAINT creator_monetization_pkey PRIMARY KEY (id);


--
-- Name: creator_payout_settings creator_payout_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_payout_settings
    ADD CONSTRAINT creator_payout_settings_pkey PRIMARY KEY (id);


--
-- Name: creator_payout_states creator_payout_states_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_payout_states
    ADD CONSTRAINT creator_payout_states_pkey PRIMARY KEY (id);


--
-- Name: creator_payouts creator_payouts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_payouts
    ADD CONSTRAINT creator_payouts_pkey PRIMARY KEY (id);


--
-- Name: creator_posts creator_posts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_posts
    ADD CONSTRAINT creator_posts_pkey PRIMARY KEY (id);


--
-- Name: creator_presence creator_presence_creator_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_presence
    ADD CONSTRAINT creator_presence_creator_id_key UNIQUE (creator_id);


--
-- Name: creator_presence creator_presence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_presence
    ADD CONSTRAINT creator_presence_pkey PRIMARY KEY (id);


--
-- Name: creator_profiles creator_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_profiles
    ADD CONSTRAINT creator_profiles_pkey PRIMARY KEY (id);


--
-- Name: creator_profiles creator_profiles_public_handle_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_profiles
    ADD CONSTRAINT creator_profiles_public_handle_key UNIQUE (public_handle);


--
-- Name: creator_profiles creator_profiles_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_profiles
    ADD CONSTRAINT creator_profiles_user_id_key UNIQUE (user_id);


--
-- Name: creator_profiles creator_profiles_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_profiles
    ADD CONSTRAINT creator_profiles_username_key UNIQUE (username);


--
-- Name: creator_records creator_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_records
    ADD CONSTRAINT creator_records_pkey PRIMARY KEY (id);


--
-- Name: creator_records creator_records_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_records
    ADD CONSTRAINT creator_records_user_id_key UNIQUE (user_id);


--
-- Name: creator_reputation_snapshot creator_reputation_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_reputation_snapshot
    ADD CONSTRAINT creator_reputation_snapshot_pkey PRIMARY KEY (creator_id);


--
-- Name: creator_stats creator_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_stats
    ADD CONSTRAINT creator_stats_pkey PRIMARY KEY (creator_id);


--
-- Name: creator_stripe_accounts creator_stripe_accounts_creator_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_stripe_accounts
    ADD CONSTRAINT creator_stripe_accounts_creator_id_key UNIQUE (creator_id);


--
-- Name: creator_stripe_accounts creator_stripe_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_stripe_accounts
    ADD CONSTRAINT creator_stripe_accounts_pkey PRIMARY KEY (id);


--
-- Name: creator_stripe_accounts creator_stripe_accounts_stripe_account_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_stripe_accounts
    ADD CONSTRAINT creator_stripe_accounts_stripe_account_id_key UNIQUE (stripe_account_id);


--
-- Name: creator_tips creator_tips_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_tips
    ADD CONSTRAINT creator_tips_pkey PRIMARY KEY (id);


--
-- Name: creator_top_content creator_top_content_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_top_content
    ADD CONSTRAINT creator_top_content_pkey PRIMARY KEY (id);


--
-- Name: creator_verification creator_verification_creator_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_verification
    ADD CONSTRAINT creator_verification_creator_id_key UNIQUE (creator_id);


--
-- Name: creator_verification creator_verification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_verification
    ADD CONSTRAINT creator_verification_pkey PRIMARY KEY (id);


--
-- Name: creators creators_creator_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creators
    ADD CONSTRAINT creators_creator_id_key UNIQUE (creator_id);


--
-- Name: creators creators_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creators
    ADD CONSTRAINT creators_pkey PRIMARY KEY (id);


--
-- Name: device_fingerprints device_fingerprints_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_fingerprints
    ADD CONSTRAINT device_fingerprints_pkey PRIMARY KEY (id);


--
-- Name: experiment_analytics experiment_analytics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.experiment_analytics
    ADD CONSTRAINT experiment_analytics_pkey PRIMARY KEY (experiment_key, variant);


--
-- Name: export_jobs export_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.export_jobs
    ADD CONSTRAINT export_jobs_pkey PRIMARY KEY (id);


--
-- Name: feature_flags feature_flags_flag_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feature_flags
    ADD CONSTRAINT feature_flags_flag_key_key UNIQUE (flag_key);


--
-- Name: feature_flags feature_flags_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feature_flags
    ADD CONSTRAINT feature_flags_pkey PRIMARY KEY (id);


--
-- Name: fraud_decisions fraud_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_decisions
    ADD CONSTRAINT fraud_decisions_pkey PRIMARY KEY (id);


--
-- Name: fraud_events fraud_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_events
    ADD CONSTRAINT fraud_events_pkey PRIMARY KEY (id);


--
-- Name: fraud_flags fraud_flags_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_flags
    ADD CONSTRAINT fraud_flags_pkey PRIMARY KEY (id);


--
-- Name: fraud_risk_assessments fraud_risk_assessments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_risk_assessments
    ADD CONSTRAINT fraud_risk_assessments_pkey PRIMARY KEY (id);


--
-- Name: fraud_risk_scores fraud_risk_scores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_risk_scores
    ADD CONSTRAINT fraud_risk_scores_pkey PRIMARY KEY (user_id);


--
-- Name: fraud_scores fraud_scores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_scores
    ADD CONSTRAINT fraud_scores_pkey PRIMARY KEY (id);


--
-- Name: fraud_scores fraud_scores_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_scores
    ADD CONSTRAINT fraud_scores_user_id_key UNIQUE (user_id);


--
-- Name: fraud_signals fraud_signals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fraud_signals
    ADD CONSTRAINT fraud_signals_pkey PRIMARY KEY (id);


--
-- Name: highlighted_messages highlighted_messages_client_request_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.highlighted_messages
    ADD CONSTRAINT highlighted_messages_client_request_id_key UNIQUE (client_request_id);


--
-- Name: highlighted_messages highlighted_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.highlighted_messages
    ADD CONSTRAINT highlighted_messages_pkey PRIMARY KEY (id);


--
-- Name: creator_analytics idx_creator_analytics_creator_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_analytics
    ADD CONSTRAINT idx_creator_analytics_creator_date UNIQUE (creator_id, date);


--
-- Name: creator_payout_settings idx_creator_payout_settings_creator; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_payout_settings
    ADD CONSTRAINT idx_creator_payout_settings_creator UNIQUE (creator_id);


--
-- Name: creator_payout_states idx_creator_payout_state_creator; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_payout_states
    ADD CONSTRAINT idx_creator_payout_state_creator UNIQUE (creator_id);


--
-- Name: leaderboard_entries idx_leaderboard_unique_entry; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leaderboard_entries
    ADD CONSTRAINT idx_leaderboard_unique_entry UNIQUE (period, creator_id, reference_date, category);


--
-- Name: user_restrictions idx_user_restriction_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_restrictions
    ADD CONSTRAINT idx_user_restriction_user UNIQUE (user_id);


--
-- Name: invoices invoices_invoice_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_invoice_number_key UNIQUE (invoice_number);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);


--
-- Name: leaderboard_entries leaderboard_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leaderboard_entries
    ADD CONSTRAINT leaderboard_entries_pkey PRIMARY KEY (id);


--
-- Name: legacy_chargebacks legacy_chargebacks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legacy_chargebacks
    ADD CONSTRAINT legacy_chargebacks_pkey PRIMARY KEY (id);


--
-- Name: legacy_creator_profiles legacy_creator_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legacy_creator_profiles
    ADD CONSTRAINT legacy_creator_profiles_pkey PRIMARY KEY (id);


--
-- Name: legacy_creator_profiles legacy_creator_profiles_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legacy_creator_profiles
    ADD CONSTRAINT legacy_creator_profiles_user_id_key UNIQUE (user_id);


--
-- Name: legacy_creator_profiles legacy_creator_profiles_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legacy_creator_profiles
    ADD CONSTRAINT legacy_creator_profiles_username_key UNIQUE (username);


--
-- Name: legacy_creator_stripe_accounts legacy_creator_stripe_accounts_creator_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legacy_creator_stripe_accounts
    ADD CONSTRAINT legacy_creator_stripe_accounts_creator_id_key UNIQUE (creator_id);


--
-- Name: legacy_creator_stripe_accounts legacy_creator_stripe_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legacy_creator_stripe_accounts
    ADD CONSTRAINT legacy_creator_stripe_accounts_pkey PRIMARY KEY (id);


--
-- Name: live_access live_access_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.live_access
    ADD CONSTRAINT live_access_pkey PRIMARY KEY (id);


--
-- Name: live_streams live_streams_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.live_streams
    ADD CONSTRAINT live_streams_pkey PRIMARY KEY (id);


--
-- Name: live_streams live_streams_stream_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.live_streams
    ADD CONSTRAINT live_streams_stream_key_key UNIQUE (stream_key);


--
-- Name: live_streams_v2 live_streams_v2_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.live_streams_v2
    ADD CONSTRAINT live_streams_v2_pkey PRIMARY KEY (id);


--
-- Name: livestream_sessions livestream_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.livestream_sessions
    ADD CONSTRAINT livestream_sessions_pkey PRIMARY KEY (id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: payout_audit_logs payout_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_audit_logs
    ADD CONSTRAINT payout_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: payout_freeze_audit_log payout_freeze_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_freeze_audit_log
    ADD CONSTRAINT payout_freeze_audit_log_pkey PRIMARY KEY (id);


--
-- Name: payout_freeze_history payout_freeze_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_freeze_history
    ADD CONSTRAINT payout_freeze_history_pkey PRIMARY KEY (id);


--
-- Name: payout_freezes payout_freezes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_freezes
    ADD CONSTRAINT payout_freezes_pkey PRIMARY KEY (id);


--
-- Name: payout_hold_audits payout_hold_audits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_hold_audits
    ADD CONSTRAINT payout_hold_audits_pkey PRIMARY KEY (id);


--
-- Name: payout_hold_policies payout_hold_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_hold_policies
    ADD CONSTRAINT payout_hold_policies_pkey PRIMARY KEY (id);


--
-- Name: payout_holds payout_holds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_holds
    ADD CONSTRAINT payout_holds_pkey PRIMARY KEY (id);


--
-- Name: payout_policy_decisions payout_policy_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_policy_decisions
    ADD CONSTRAINT payout_policy_decisions_pkey PRIMARY KEY (id);


--
-- Name: payout_requests payout_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_requests
    ADD CONSTRAINT payout_requests_pkey PRIMARY KEY (id);


--
-- Name: payout_risks payout_risks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payout_risks
    ADD CONSTRAINT payout_risks_pkey PRIMARY KEY (id);


--
-- Name: payouts payouts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payouts
    ADD CONSTRAINT payouts_pkey PRIMARY KEY (id);


--
-- Name: platform_analytics platform_analytics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_analytics
    ADD CONSTRAINT platform_analytics_pkey PRIMARY KEY (date);


--
-- Name: platform_balances platform_balances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.platform_balances
    ADD CONSTRAINT platform_balances_pkey PRIMARY KEY (id);


--
-- Name: post_likes post_likes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT post_likes_pkey PRIMARY KEY (id);


--
-- Name: post_likes post_likes_user_id_post_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT post_likes_user_id_post_id_key UNIQUE (user_id, post_id);


--
-- Name: ppv_chat_access ppv_chat_access_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppv_chat_access
    ADD CONSTRAINT ppv_chat_access_pkey PRIMARY KEY (id);


--
-- Name: ppv_content ppv_content_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppv_content
    ADD CONSTRAINT ppv_content_pkey PRIMARY KEY (id);


--
-- Name: ppv_purchases ppv_purchases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppv_purchases
    ADD CONSTRAINT ppv_purchases_pkey PRIMARY KEY (id);


--
-- Name: private_sessions private_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.private_sessions
    ADD CONSTRAINT private_sessions_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_key UNIQUE (token);


--
-- Name: reports reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reports
    ADD CONSTRAINT reports_pkey PRIMARY KEY (id);


--
-- Name: reputation_change_logs reputation_change_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reputation_change_logs
    ADD CONSTRAINT reputation_change_logs_pkey PRIMARY KEY (id);


--
-- Name: reputation_events reputation_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reputation_events
    ADD CONSTRAINT reputation_events_pkey PRIMARY KEY (id);


--
-- Name: risk_decision_audits risk_decision_audits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_decision_audits
    ADD CONSTRAINT risk_decision_audits_pkey PRIMARY KEY (id);


--
-- Name: risk_explanation_logs risk_explanation_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_explanation_logs
    ADD CONSTRAINT risk_explanation_logs_pkey PRIMARY KEY (id);


--
-- Name: risk_explanations risk_explanations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_explanations
    ADD CONSTRAINT risk_explanations_pkey PRIMARY KEY (id);


--
-- Name: risk_scores risk_scores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_scores
    ADD CONSTRAINT risk_scores_pkey PRIMARY KEY (user_id);


--
-- Name: rule_fraud_signals rule_fraud_signals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rule_fraud_signals
    ADD CONSTRAINT rule_fraud_signals_pkey PRIMARY KEY (id);


--
-- Name: slow_mode_bypass slow_mode_bypass_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.slow_mode_bypass
    ADD CONSTRAINT slow_mode_bypass_pkey PRIMARY KEY (id);


--
-- Name: stream_rooms stream_rooms_creator_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stream_rooms
    ADD CONSTRAINT stream_rooms_creator_id_key UNIQUE (creator_id);


--
-- Name: stream_rooms stream_rooms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stream_rooms
    ADD CONSTRAINT stream_rooms_pkey PRIMARY KEY (id);


--
-- Name: stripe_accounts stripe_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stripe_accounts
    ADD CONSTRAINT stripe_accounts_pkey PRIMARY KEY (id);


--
-- Name: stripe_accounts stripe_accounts_stripe_account_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stripe_accounts
    ADD CONSTRAINT stripe_accounts_stripe_account_id_key UNIQUE (stripe_account_id);


--
-- Name: stripe_accounts stripe_accounts_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stripe_accounts
    ADD CONSTRAINT stripe_accounts_user_id_key UNIQUE (user_id);


--
-- Name: stripe_webhook_events stripe_webhook_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stripe_webhook_events
    ADD CONSTRAINT stripe_webhook_events_pkey PRIMARY KEY (event_id);


--
-- Name: subscriptions subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);


--
-- Name: super_tips super_tips_client_request_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.super_tips
    ADD CONSTRAINT super_tips_client_request_id_key UNIQUE (client_request_id);


--
-- Name: super_tips super_tips_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.super_tips
    ADD CONSTRAINT super_tips_pkey PRIMARY KEY (id);


--
-- Name: tip_actions tip_actions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tip_actions
    ADD CONSTRAINT tip_actions_pkey PRIMARY KEY (id);


--
-- Name: tip_goals tip_goals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tip_goals
    ADD CONSTRAINT tip_goals_pkey PRIMARY KEY (id);


--
-- Name: tip_records tip_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tip_records
    ADD CONSTRAINT tip_records_pkey PRIMARY KEY (id);


--
-- Name: tips tips_client_request_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tips
    ADD CONSTRAINT tips_client_request_id_key UNIQUE (client_request_id);


--
-- Name: tips tips_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tips
    ADD CONSTRAINT tips_pkey PRIMARY KEY (id);


--
-- Name: token_packages token_packages_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.token_packages
    ADD CONSTRAINT token_packages_name_key UNIQUE (name);


--
-- Name: token_packages token_packages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.token_packages
    ADD CONSTRAINT token_packages_pkey PRIMARY KEY (id);


--
-- Name: token_transactions token_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.token_transactions
    ADD CONSTRAINT token_transactions_pkey PRIMARY KEY (id);


--
-- Name: device_fingerprints uk_device_fingerprints_user_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_fingerprints
    ADD CONSTRAINT uk_device_fingerprints_user_hash UNIQUE (user_id, fingerprint_hash);


--
-- Name: ppv_chat_access uk_ppv_chat_access_user_room_ppv; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppv_chat_access
    ADD CONSTRAINT uk_ppv_chat_access_user_room_ppv UNIQUE (user_id, room_id, ppv_content_id);


--
-- Name: slow_mode_bypass uk_slow_mode_bypass_user_room; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.slow_mode_bypass
    ADD CONSTRAINT uk_slow_mode_bypass_user_room UNIQUE (user_id, room_id);


--
-- Name: velocity_metrics uk_velocity_metrics_user_action_window; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.velocity_metrics
    ADD CONSTRAINT uk_velocity_metrics_user_action_window UNIQUE (user_id, action_type, window_start, window_end);


--
-- Name: weekly_tip_leaderboards uk_weekly_tip_leaderboard; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.weekly_tip_leaderboards
    ADD CONSTRAINT uk_weekly_tip_leaderboard UNIQUE (creator_id, username, week_number, year);


--
-- Name: user_badges user_badges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_badges
    ADD CONSTRAINT user_badges_pkey PRIMARY KEY (id);


--
-- Name: user_restrictions user_restrictions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_restrictions
    ADD CONSTRAINT user_restrictions_pkey PRIMARY KEY (id);


--
-- Name: user_risk_state user_risk_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_risk_state
    ADD CONSTRAINT user_risk_state_pkey PRIMARY KEY (user_id);


--
-- Name: user_wallets user_wallets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_wallets
    ADD CONSTRAINT user_wallets_pkey PRIMARY KEY (id);


--
-- Name: user_wallets user_wallets_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_wallets
    ADD CONSTRAINT user_wallets_user_id_key UNIQUE (user_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_stripe_account_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_stripe_account_id_key UNIQUE (stripe_account_id);


--
-- Name: velocity_metrics velocity_metrics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.velocity_metrics
    ADD CONSTRAINT velocity_metrics_pkey PRIMARY KEY (id);


--
-- Name: wallet_transactions wallet_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT wallet_transactions_pkey PRIMARY KEY (id);


--
-- Name: webhook_events webhook_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.webhook_events
    ADD CONSTRAINT webhook_events_pkey PRIMARY KEY (id);


--
-- Name: webhook_events webhook_events_stripe_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.webhook_events
    ADD CONSTRAINT webhook_events_stripe_event_id_key UNIQUE (stripe_event_id);


--
-- Name: weekly_tip_leaderboards weekly_tip_leaderboards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.weekly_tip_leaderboards
    ADD CONSTRAINT weekly_tip_leaderboards_pkey PRIMARY KEY (id);


--
-- Name: idx_abuse_event_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_abuse_event_created ON public.abuse_events USING btree (created_at);


--
-- Name: idx_abuse_event_ip; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_abuse_event_ip ON public.abuse_events USING btree (ip_address);


--
-- Name: idx_abuse_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_abuse_event_type ON public.abuse_events USING btree (event_type);


--
-- Name: idx_abuse_event_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_abuse_event_user ON public.abuse_events USING btree (user_id);


--
-- Name: idx_abuse_report_reporter_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_abuse_report_reporter_id ON public.abuse_reports USING btree (reporter_id);


--
-- Name: idx_abuse_report_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_abuse_report_status ON public.abuse_reports USING btree (status);


--
-- Name: idx_aml_incident_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_aml_incident_created_at ON public.aml_incidents USING btree (created_at);


--
-- Name: idx_aml_incident_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_aml_incident_user_id ON public.aml_incidents USING btree (user_id);


--
-- Name: idx_aml_risk_score_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_aml_risk_score_user_id ON public.aml_risk_scores USING btree (user_id);


--
-- Name: idx_analytics_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_analytics_created_at ON public.analytics_events USING btree (created_at);


--
-- Name: idx_analytics_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_analytics_event_type ON public.analytics_events USING btree (event_type);


--
-- Name: idx_analytics_funnel_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_analytics_funnel_id ON public.analytics_events USING btree (funnel_id);


--
-- Name: idx_analytics_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_analytics_user_id ON public.analytics_events USING btree (user_id);


--
-- Name: idx_audit_log_actor; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_actor ON public.audit_logs USING btree (actor_user_id);


--
-- Name: idx_audit_log_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_created_at ON public.audit_logs USING btree (created_at);


--
-- Name: idx_audit_log_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_log_target ON public.audit_logs USING btree (target_type, target_id);


--
-- Name: idx_chargeback_audit_chargeback; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_audit_chargeback ON public.chargeback_audits USING btree (chargeback_id);


--
-- Name: idx_chargeback_audit_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_audit_created_at ON public.chargeback_audits USING btree (created_at);


--
-- Name: idx_chargeback_audit_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_audit_user ON public.chargeback_audits USING btree (user_id);


--
-- Name: idx_chargeback_case_pi; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_case_pi ON public.chargeback_cases USING btree (payment_intent_id);


--
-- Name: idx_chargeback_case_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_case_user ON public.chargeback_cases USING btree (user_id);


--
-- Name: idx_chargeback_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_creator ON public.legacy_chargebacks USING btree (creator_id);


--
-- Name: idx_chargeback_device_fingerprint; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_device_fingerprint ON public.legacy_chargebacks USING btree (device_fingerprint);


--
-- Name: idx_chargeback_ip_address; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_ip_address ON public.legacy_chargebacks USING btree (ip_address);


--
-- Name: idx_chargeback_pm_fingerprint; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_pm_fingerprint ON public.legacy_chargebacks USING btree (payment_method_fingerprint);


--
-- Name: idx_chargeback_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_status ON public.chargebacks USING btree (status);


--
-- Name: idx_chargeback_stripe_charge; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_stripe_charge ON public.chargebacks USING btree (stripe_charge_id);


--
-- Name: idx_chargeback_stripe_dispute; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_stripe_dispute ON public.legacy_chargebacks USING btree (stripe_dispute_id);


--
-- Name: idx_chargeback_transaction; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_transaction ON public.legacy_chargebacks USING btree (transaction_id);


--
-- Name: idx_chargeback_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chargeback_user ON public.chargebacks USING btree (user_id);


--
-- Name: idx_chat_room_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_room_creator ON public.chat_rooms USING btree (created_by);


--
-- Name: idx_chat_room_is_live; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_room_is_live ON public.chat_rooms USING btree (is_live);


--
-- Name: idx_chat_violation_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_violation_creator ON public.chat_violation_logs USING btree (creator_id);


--
-- Name: idx_chat_violation_timestamp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_violation_timestamp ON public.chat_violation_logs USING btree ("timestamp");


--
-- Name: idx_chat_violation_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_violation_user ON public.chat_violation_logs USING btree (user_id);


--
-- Name: idx_chatmsg_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatmsg_created_at ON public.chat_messages USING btree (created_at);


--
-- Name: idx_chatmsg_room; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatmsg_room ON public.chat_messages USING btree (room_id);


--
-- Name: idx_chatmsg_sender; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatmsg_sender ON public.chat_messages USING btree (sender_id);


--
-- Name: idx_chatroom_created_at_v2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatroom_created_at_v2 ON public.chat_rooms_v2 USING btree (created_at);


--
-- Name: idx_chatroom_creator_v2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatroom_creator_v2 ON public.chat_rooms_v2 USING btree (creator_id);


--
-- Name: idx_chatroom_is_live_v2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chatroom_is_live_v2 ON public.chat_rooms_v2 USING btree (is_live);


--
-- Name: idx_collusion_record_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collusion_record_creator ON public.creator_collusion_records USING btree (creator_id);


--
-- Name: idx_collusion_record_evaluated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_collusion_record_evaluated_at ON public.creator_collusion_records USING btree (evaluated_at);


--
-- Name: idx_content_analytics_creator_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_analytics_creator_date ON public.content_analytics USING btree (creator_id, date);


--
-- Name: idx_creator_analytics_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_creator_analytics_date ON public.creator_analytics USING btree (date);


--
-- Name: idx_creator_earning_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_creator_earning_creator ON public.creator_earnings_history USING btree (creator_id);


--
-- Name: idx_creator_earning_source_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_creator_earning_source_type ON public.creator_earnings_history USING btree (source_type);


--
-- Name: idx_creator_earnings_inv_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_creator_earnings_inv_creator ON public.creator_earnings_invoices USING btree (creator_id);


--
-- Name: idx_creator_earnings_inv_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_creator_earnings_inv_status ON public.creator_earnings_invoices USING btree (status);


--
-- Name: idx_creator_payout_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_creator_payout_creator ON public.creator_payouts USING btree (creator_id);


--
-- Name: idx_creator_payout_state_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_creator_payout_state_status ON public.creator_payout_states USING btree (status);


--
-- Name: idx_creator_payout_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_creator_payout_status ON public.creator_payouts USING btree (status);


--
-- Name: idx_creator_reputation_snapshot_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_creator_reputation_snapshot_status ON public.creator_reputation_snapshot USING btree (status);


--
-- Name: idx_creator_top_content_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_creator_top_content_creator ON public.creator_top_content USING btree (creator_id);


--
-- Name: idx_device_fingerprints_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_device_fingerprints_hash ON public.device_fingerprints USING btree (fingerprint_hash);


--
-- Name: idx_device_fingerprints_ip_address; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_device_fingerprints_ip_address ON public.device_fingerprints USING btree (ip_address);


--
-- Name: idx_device_fingerprints_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_device_fingerprints_user_id ON public.device_fingerprints USING btree (user_id);


--
-- Name: idx_fraud_event_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_event_created_at ON public.fraud_events USING btree (created_at);


--
-- Name: idx_fraud_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_event_type ON public.fraud_events USING btree (event_type);


--
-- Name: idx_fraud_event_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_event_user_id ON public.fraud_events USING btree (user_id);


--
-- Name: idx_fraud_flag_score; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_flag_score ON public.fraud_flags USING btree (score);


--
-- Name: idx_fraud_flag_stripe_charge; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_flag_stripe_charge ON public.fraud_flags USING btree (stripe_charge_id);


--
-- Name: idx_fraud_flag_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_flag_user_id ON public.fraud_flags USING btree (user_id);


--
-- Name: idx_fraud_risk_assessment_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_risk_assessment_created_at ON public.fraud_risk_assessments USING btree (created_at);


--
-- Name: idx_fraud_risk_assessment_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_risk_assessment_user_id ON public.fraud_risk_assessments USING btree (user_id);


--
-- Name: idx_fraud_signal_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_signal_created ON public.fraud_signals USING btree (created_at);


--
-- Name: idx_fraud_signal_room; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_signal_room ON public.fraud_signals USING btree (room_id);


--
-- Name: idx_fraud_signal_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fraud_signal_user ON public.fraud_signals USING btree (user_id);


--
-- Name: idx_highlighted_msg_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_highlighted_msg_created_at ON public.highlighted_messages USING btree (created_at);


--
-- Name: idx_highlighted_msg_message_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_highlighted_msg_message_id ON public.highlighted_messages USING btree (message_id);


--
-- Name: idx_highlighted_msg_room_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_highlighted_msg_room_id ON public.highlighted_messages USING btree (room_id);


--
-- Name: idx_highlighted_msg_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_highlighted_msg_user_id ON public.highlighted_messages USING btree (user_id);


--
-- Name: idx_invoice_stripe_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invoice_stripe_id ON public.invoices USING btree (stripe_invoice_id);


--
-- Name: idx_invoice_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_invoice_user ON public.invoices USING btree (user_id);


--
-- Name: idx_leaderboard_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leaderboard_category ON public.leaderboard_entries USING btree (category);


--
-- Name: idx_leaderboard_period_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leaderboard_period_creator ON public.leaderboard_entries USING btree (period, creator_id);


--
-- Name: idx_leaderboard_period_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leaderboard_period_date ON public.leaderboard_entries USING btree (period, reference_date);


--
-- Name: idx_leaderboard_period_rank; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_leaderboard_period_rank ON public.leaderboard_entries USING btree (period, leaderboard_rank);


--
-- Name: idx_live_access_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_live_access_lookup ON public.live_access USING btree (creator_user_id, viewer_user_id);


--
-- Name: idx_livestream_creator_v2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_livestream_creator_v2 ON public.live_streams_v2 USING btree (creator_id);


--
-- Name: idx_livestream_session_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_livestream_session_creator ON public.livestream_sessions USING btree (creator_id);


--
-- Name: idx_livestream_session_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_livestream_session_status ON public.livestream_sessions USING btree (status);


--
-- Name: idx_livestream_started_at_v2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_livestream_started_at_v2 ON public.live_streams_v2 USING btree (started_at);


--
-- Name: idx_livestream_status_v2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_livestream_status_v2 ON public.live_streams_v2 USING btree (status);


--
-- Name: idx_moderation_room; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_moderation_room ON public.chat_moderations USING btree (room_id);


--
-- Name: idx_moderation_target_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_moderation_target_user ON public.chat_moderations USING btree (target_user_id);


--
-- Name: idx_payment_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_user ON public.payments USING btree (user_id);


--
-- Name: idx_payout_audit_log_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_audit_log_created_at ON public.payout_audit_logs USING btree (created_at);


--
-- Name: idx_payout_audit_log_payout; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_audit_log_payout ON public.payout_audit_logs USING btree (payout_id);


--
-- Name: idx_payout_freeze_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_freeze_creator ON public.payout_freezes USING btree (creator_id);


--
-- Name: idx_payout_freeze_history_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_freeze_history_created_at ON public.payout_freeze_history USING btree (created_at);


--
-- Name: idx_payout_freeze_history_creator_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_freeze_history_creator_id ON public.payout_freeze_history USING btree (creator_id);


--
-- Name: idx_payout_hold_audit_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_hold_audit_created_at ON public.payout_hold_audits USING btree (created_at);


--
-- Name: idx_payout_hold_audit_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_hold_audit_subject ON public.payout_hold_audits USING btree (subject_type, subject_id);


--
-- Name: idx_payout_hold_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_hold_created_at ON public.payout_holds USING btree (created_at);


--
-- Name: idx_payout_hold_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_hold_expires_at ON public.payout_hold_policies USING btree (expires_at);


--
-- Name: idx_payout_hold_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_hold_subject ON public.payout_hold_policies USING btree (subject_type, subject_id);


--
-- Name: idx_payout_hold_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_hold_user_id ON public.payout_holds USING btree (user_id);


--
-- Name: idx_payout_policy_decision_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_policy_decision_creator ON public.payout_policy_decisions USING btree (creator_id);


--
-- Name: idx_payout_request_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_request_creator ON public.payout_requests USING btree (creator_id);


--
-- Name: idx_payout_request_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_request_status ON public.payout_requests USING btree (status);


--
-- Name: idx_payout_risk_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_risk_user ON public.payout_risks USING btree (user_id);


--
-- Name: idx_payout_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_status ON public.payouts USING btree (status);


--
-- Name: idx_payout_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payout_user ON public.payouts USING btree (user_id);


--
-- Name: idx_private_session_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_private_session_creator ON public.private_sessions USING btree (creator_id);


--
-- Name: idx_private_session_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_private_session_status ON public.private_sessions USING btree (status);


--
-- Name: idx_private_session_viewer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_private_session_viewer ON public.private_sessions USING btree (viewer_id);


--
-- Name: idx_report_reported_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_reported_id ON public.reports USING btree (reported_user_id);


--
-- Name: idx_report_reporter_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_reporter_id ON public.reports USING btree (reporter_user_id);


--
-- Name: idx_report_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_report_status ON public.reports USING btree (status);


--
-- Name: idx_reputation_change_log_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reputation_change_log_creator ON public.reputation_change_logs USING btree (creator_id);


--
-- Name: idx_reputation_event_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reputation_event_created_at ON public.reputation_events USING btree (created_at);


--
-- Name: idx_reputation_event_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reputation_event_creator ON public.reputation_events USING btree (creator_id);


--
-- Name: idx_reputation_event_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reputation_event_type ON public.reputation_events USING btree (type);


--
-- Name: idx_risk_audit_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_audit_created_at ON public.risk_decision_audits USING btree (created_at);


--
-- Name: idx_risk_audit_transaction; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_audit_transaction ON public.risk_decision_audits USING btree (transaction_id);


--
-- Name: idx_risk_audit_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_audit_user ON public.risk_decision_audits USING btree (user_id);


--
-- Name: idx_risk_explanation_generated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_explanation_generated_at ON public.risk_explanations USING btree (generated_at);


--
-- Name: idx_risk_explanation_log_explanation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_explanation_log_explanation ON public.risk_explanation_logs USING btree (explanation_id);


--
-- Name: idx_risk_explanation_log_requester; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_explanation_log_requester ON public.risk_explanation_logs USING btree (requester_id);


--
-- Name: idx_risk_explanation_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_explanation_subject ON public.risk_explanations USING btree (subject_type, subject_id);


--
-- Name: idx_rule_fraud_signal_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rule_fraud_signal_created_at ON public.rule_fraud_signals USING btree (created_at);


--
-- Name: idx_rule_fraud_signal_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rule_fraud_signal_user_id ON public.rule_fraud_signals USING btree (user_id);


--
-- Name: idx_stream_room_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stream_room_creator ON public.stream_rooms USING btree (creator_id);


--
-- Name: idx_subscription_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_subscription_user ON public.subscriptions USING btree (user_id);


--
-- Name: idx_supertip_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_supertip_created_at ON public.super_tips USING btree (created_at);


--
-- Name: idx_supertip_room; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_supertip_room ON public.super_tips USING btree (room_id);


--
-- Name: idx_tip_creator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tip_creator ON public.tip_records USING btree (creator_id);


--
-- Name: idx_tip_from_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tip_from_user ON public.tips USING btree (from_user_id);


--
-- Name: idx_tip_room; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tip_room ON public.tip_records USING btree (room_id);


--
-- Name: idx_tip_room_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tip_room_id ON public.tips USING btree (room_id);


--
-- Name: idx_tip_viewer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tip_viewer ON public.tip_records USING btree (viewer_id);


--
-- Name: idx_user_risk_state_current_risk; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_risk_state_current_risk ON public.user_risk_state USING btree (current_risk);


--
-- Name: idx_velocity_metrics_action_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_velocity_metrics_action_type ON public.velocity_metrics USING btree (action_type);


--
-- Name: idx_velocity_metrics_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_velocity_metrics_user_id ON public.velocity_metrics USING btree (user_id);


--
-- Name: idx_velocity_metrics_window_end; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_velocity_metrics_window_end ON public.velocity_metrics USING btree (window_end);


--
-- Name: idx_wallet_transaction_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wallet_transaction_user ON public.wallet_transactions USING btree (user_id);


--
-- Name: content fk14eidkbpu6n090ymygps0k1w; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content
    ADD CONSTRAINT fk14eidkbpu6n090ymygps0k1w FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: refresh_tokens fk1lih5y2npsf8u5o3vhdb9y0os; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk1lih5y2npsf8u5o3vhdb9y0os FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: private_sessions fk1mirm2easybi3j3o7q583oirr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.private_sessions
    ADD CONSTRAINT fk1mirm2easybi3j3o7q583oirr FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: chargebacks fk1qollrnf45gnrf3e8lhev0025; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chargebacks
    ADD CONSTRAINT fk1qollrnf45gnrf3e8lhev0025 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: creator_earnings_history fk1u1jc7hy0yh2tlqdwxq7x4g19; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_history
    ADD CONSTRAINT fk1u1jc7hy0yh2tlqdwxq7x4g19 FOREIGN KEY (payout_id) REFERENCES public.creator_payouts(id);


--
-- Name: tip_records fk1we07cblrgibrvbnjpehf255n; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tip_records
    ADD CONSTRAINT fk1we07cblrgibrvbnjpehf255n FOREIGN KEY (viewer_id) REFERENCES public.users(id);


--
-- Name: tips fk22ttlly3mmmef9gmb8w1duuex; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tips
    ADD CONSTRAINT fk22ttlly3mmmef9gmb8w1duuex FOREIGN KEY (from_user_id) REFERENCES public.users(id);


--
-- Name: creator_verification fk2f495n5vyb5yynd6gso725mtw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_verification
    ADD CONSTRAINT fk2f495n5vyb5yynd6gso725mtw FOREIGN KEY (creator_id) REFERENCES public.creator_records(id);


--
-- Name: creator_posts fk2l511r4oyn4q7owicvbu3em7m; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_posts
    ADD CONSTRAINT fk2l511r4oyn4q7owicvbu3em7m FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: highlighted_messages fk2th2st3ybtcsvfoj1a09rtxg6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.highlighted_messages
    ADD CONSTRAINT fk2th2st3ybtcsvfoj1a09rtxg6 FOREIGN KEY (room_id) REFERENCES public.stream_rooms(id);


--
-- Name: user_wallets fk423n8ap6gdudl8fcab7ugv3qt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_wallets
    ADD CONSTRAINT fk423n8ap6gdudl8fcab7ugv3qt FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: creator_profiles fk48jx3726hqfmcyksfm6rysgw1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_profiles
    ADD CONSTRAINT fk48jx3726hqfmcyksfm6rysgw1 FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: ppv_purchases fk4i31b2ou0m4je42jbotadgq87; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppv_purchases
    ADD CONSTRAINT fk4i31b2ou0m4je42jbotadgq87 FOREIGN KEY (ppv_content_id) REFERENCES public.ppv_content(id);


--
-- Name: super_tips fk5bkc03b1jupkr9v334fbewvnw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.super_tips
    ADD CONSTRAINT fk5bkc03b1jupkr9v334fbewvnw FOREIGN KEY (from_user_id) REFERENCES public.users(id);


--
-- Name: super_tips fk5l9kxu6t94pb0sii0wkb0fn81; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.super_tips
    ADD CONSTRAINT fk5l9kxu6t94pb0sii0wkb0fn81 FOREIGN KEY (room_id) REFERENCES public.stream_rooms(id);


--
-- Name: creator_tips fk7mln58uhfjdaddm37opw8rhye; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_tips
    ADD CONSTRAINT fk7mln58uhfjdaddm37opw8rhye FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: creator_earnings_history fk7xv4m4v9ngay2hude9qb46e58; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_history
    ADD CONSTRAINT fk7xv4m4v9ngay2hude9qb46e58 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: creator_earnings_history fk8ebq9y4s4jj6dogt76gy3o7w8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_history
    ADD CONSTRAINT fk8ebq9y4s4jj6dogt76gy3o7w8 FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: abuse_reports fk8ky83nyxty106ebf7llq9f8ul; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abuse_reports
    ADD CONSTRAINT fk8ky83nyxty106ebf7llq9f8ul FOREIGN KEY (target_user_id) REFERENCES public.users(id);


--
-- Name: payments fk8y0kvmbql5i3hdjariwn6jh5d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk8y0kvmbql5i3hdjariwn6jh5d FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: creator_earnings_history fk93lc3ekrj1app2q7kq7gl1x12; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_history
    ADD CONSTRAINT fk93lc3ekrj1app2q7kq7gl1x12 FOREIGN KEY (hold_policy_id) REFERENCES public.payout_hold_policies(id);


--
-- Name: tips fk9c7d1irowgbvc5o8yywq6hu09; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tips
    ADD CONSTRAINT fk9c7d1irowgbvc5o8yywq6hu09 FOREIGN KEY (room_id) REFERENCES public.stream_rooms(id);


--
-- Name: creator_earnings_history fk9cve4y94d6sng0copvx56uiot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_history
    ADD CONSTRAINT fk9cve4y94d6sng0copvx56uiot FOREIGN KEY (payout_hold_id) REFERENCES public.payout_holds(id);


--
-- Name: stripe_accounts fk9gc8w71yrgh4we84na6657seb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stripe_accounts
    ADD CONSTRAINT fk9gc8w71yrgh4we84na6657seb FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: analytics_events fk9mbm09lhrtgo9h4hh3sf0o68g; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.analytics_events
    ADD CONSTRAINT fk9mbm09lhrtgo9h4hh3sf0o68g FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: legacy_creator_profiles fk9voin8aoq9nl9f8bpfoj3vf1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.legacy_creator_profiles
    ADD CONSTRAINT fk9voin8aoq9nl9f8bpfoj3vf1 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: invoices fkbwr4d4vyqf2bkoetxtt8j9dx7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fkbwr4d4vyqf2bkoetxtt8j9dx7 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: tip_records fkc0fyhnn475qi4nvrx8awhquyr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tip_records
    ADD CONSTRAINT fkc0fyhnn475qi4nvrx8awhquyr FOREIGN KEY (room_id) REFERENCES public.stream_rooms(id);


--
-- Name: creator_earnings_history fkccn54vd3k1c18v4hf9eb342of; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_history
    ADD CONSTRAINT fkccn54vd3k1c18v4hf9eb342of FOREIGN KEY (invoice_id) REFERENCES public.creator_earnings_invoices(id);


--
-- Name: ppv_chat_access fkdmh0xodv2u3te9mf1im6k5wjy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppv_chat_access
    ADD CONSTRAINT fkdmh0xodv2u3te9mf1im6k5wjy FOREIGN KEY (room_id) REFERENCES public.stream_rooms(id);


--
-- Name: stream_rooms fkdyuk7mopd2gn72p270m5hn879; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stream_rooms
    ADD CONSTRAINT fkdyuk7mopd2gn72p270m5hn879 FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: ppv_chat_access fkfs3dlpbm8vsjhqhy9bu4fiykk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppv_chat_access
    ADD CONSTRAINT fkfs3dlpbm8vsjhqhy9bu4fiykk FOREIGN KEY (ppv_content_id) REFERENCES public.ppv_content(id);


--
-- Name: slow_mode_bypass fkgbj5fdrejp5e6linpdtnyydij; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.slow_mode_bypass
    ADD CONSTRAINT fkgbj5fdrejp5e6linpdtnyydij FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: livestream_sessions fkgblhepsucegyb2jvwreihlbkx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.livestream_sessions
    ADD CONSTRAINT fkgblhepsucegyb2jvwreihlbkx FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: chat_rooms fkgg28hjbqauj4dc6u97n26k4j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_rooms
    ADD CONSTRAINT fkgg28hjbqauj4dc6u97n26k4j FOREIGN KEY (ppv_content_id) REFERENCES public.ppv_content(id);


--
-- Name: creator_earnings_invoices fkgixidrdcmi3o46bita602tkya; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_invoices
    ADD CONSTRAINT fkgixidrdcmi3o46bita602tkya FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: creator_earnings fkgqwn3l1v6nsdolegsrndgtavu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings
    ADD CONSTRAINT fkgqwn3l1v6nsdolegsrndgtavu FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: creator_follow fkgwjgrvj2uor5jv1qgunr7tvgh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_follow
    ADD CONSTRAINT fkgwjgrvj2uor5jv1qgunr7tvgh FOREIGN KEY (follower_id) REFERENCES public.users(id);


--
-- Name: payouts fkh29huk0car81wx59yd3sbh8ly; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payouts
    ADD CONSTRAINT fkh29huk0car81wx59yd3sbh8ly FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: subscriptions fkhro52ohfqfbay9774bev0qinr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscriptions
    ADD CONSTRAINT fkhro52ohfqfbay9774bev0qinr FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: chat_rooms fkin9277aywbjursj2b4e3bmw3s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_rooms
    ADD CONSTRAINT fkin9277aywbjursj2b4e3bmw3s FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- Name: token_transactions fkj50scgwnwl6x0pjgt3qr2lng7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.token_transactions
    ADD CONSTRAINT fkj50scgwnwl6x0pjgt3qr2lng7 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: payments fkj94hgy9v5fw1munb90tar2eje; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fkj94hgy9v5fw1munb90tar2eje FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: creator_stripe_accounts fkjbtqvggr1hybxk45i4o94ldig; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_stripe_accounts
    ADD CONSTRAINT fkjbtqvggr1hybxk45i4o94ldig FOREIGN KEY (creator_id) REFERENCES public.creator_profiles(id) ON DELETE CASCADE;


--
-- Name: super_tips fkjqtc04q4xlv3d9qhcm7x837ic; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.super_tips
    ADD CONSTRAINT fkjqtc04q4xlv3d9qhcm7x837ic FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: creator_records fkjsbh7be8uios1gvqkwsvt526b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_records
    ADD CONSTRAINT fkjsbh7be8uios1gvqkwsvt526b FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_badges fkk6e00pguaij0uke6xr81gt045; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_badges
    ADD CONSTRAINT fkk6e00pguaij0uke6xr81gt045 FOREIGN KEY (badge_id) REFERENCES public.badges(id);


--
-- Name: ppv_purchases fkkblb00rany3s3v8q4tnik3vik; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppv_purchases
    ADD CONSTRAINT fkkblb00rany3s3v8q4tnik3vik FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: post_likes fkkgau5n0nlewg6o9lr4yibqgxj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT fkkgau5n0nlewg6o9lr4yibqgxj FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: tips fklsysbnpco4sbym4f7wd2qaxfd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tips
    ADD CONSTRAINT fklsysbnpco4sbym4f7wd2qaxfd FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: creator_earnings_balances fkm8cyfaxnpkgsybmtyoow4u5p; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_balances
    ADD CONSTRAINT fkm8cyfaxnpkgsybmtyoow4u5p FOREIGN KEY (creator_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: private_sessions fkmofh77w3ak46hyojlt0iqx5av; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.private_sessions
    ADD CONSTRAINT fkmofh77w3ak46hyojlt0iqx5av FOREIGN KEY (viewer_id) REFERENCES public.users(id);


--
-- Name: creator_earnings_history fkomuet9tfc93hl5wsiatbahtxx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_earnings_history
    ADD CONSTRAINT fkomuet9tfc93hl5wsiatbahtxx FOREIGN KEY (payout_request_id) REFERENCES public.payout_requests(id);


--
-- Name: creator_monetization fkoscchh9t49oks2epjejipqo6u; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_monetization
    ADD CONSTRAINT fkoscchh9t49oks2epjejipqo6u FOREIGN KEY (creator_profile_id) REFERENCES public.creator_profiles(id) ON DELETE CASCADE;


--
-- Name: abuse_reports fkot4vuuy6lcqkl42yxliychy0e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.abuse_reports
    ADD CONSTRAINT fkot4vuuy6lcqkl42yxliychy0e FOREIGN KEY (reporter_id) REFERENCES public.users(id);


--
-- Name: creator_follow fkq8oc1u94otmb7bmi811qlb281; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_follow
    ADD CONSTRAINT fkq8oc1u94otmb7bmi811qlb281 FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: post_likes fkqfapa12qpnrbg9gh528af8t27; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_likes
    ADD CONSTRAINT fkqfapa12qpnrbg9gh528af8t27 FOREIGN KEY (post_id) REFERENCES public.creator_posts(id);


--
-- Name: highlighted_messages fkqi1i12m7alypxrgspxflec8gj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.highlighted_messages
    ADD CONSTRAINT fkqi1i12m7alypxrgspxflec8gj FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: tip_records fkqnjf405n8hb6uah41ll5gwaue; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tip_records
    ADD CONSTRAINT fkqnjf405n8hb6uah41ll5gwaue FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: user_badges fkr46ah81sjymsn035m4ojstn5s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_badges
    ADD CONSTRAINT fkr46ah81sjymsn035m4ojstn5s FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: slow_mode_bypass fkr6d8rdlvp2wjbrkqg7qykkwhx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.slow_mode_bypass
    ADD CONSTRAINT fkr6d8rdlvp2wjbrkqg7qykkwhx FOREIGN KEY (room_id) REFERENCES public.stream_rooms(id);


--
-- Name: chat_moderations fkrjq7kmx0wq300vrp85jnuwurw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_moderations
    ADD CONSTRAINT fkrjq7kmx0wq300vrp85jnuwurw FOREIGN KEY (target_user_id) REFERENCES public.users(id);


--
-- Name: chat_moderations fkrkmta44tecxqi68cebukh9xm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_moderations
    ADD CONSTRAINT fkrkmta44tecxqi68cebukh9xm FOREIGN KEY (moderator_id) REFERENCES public.users(id);


--
-- Name: wallet_transactions fkrtsa3qtjhd0rn4xb92na03vd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wallet_transactions
    ADD CONSTRAINT fkrtsa3qtjhd0rn4xb92na03vd FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: ppv_content fksr7ql3fy3h8iym16aoyf2sjc8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppv_content
    ADD CONSTRAINT fksr7ql3fy3h8iym16aoyf2sjc8 FOREIGN KEY (creator_id) REFERENCES public.users(id);


--
-- Name: highlighted_messages fktcm75f2hgl5w3k7qrx3sgsd25; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.highlighted_messages
    ADD CONSTRAINT fktcm75f2hgl5w3k7qrx3sgsd25 FOREIGN KEY (moderated_by) REFERENCES public.users(id);


--
-- Name: creator_tips fktjg605lsuv55sm6uwuo0mnyt0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.creator_tips
    ADD CONSTRAINT fktjg605lsuv55sm6uwuo0mnyt0 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: ppv_chat_access fktkkoui66r6cby54nnfkejm0ge; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ppv_chat_access
    ADD CONSTRAINT fktkkoui66r6cby54nnfkejm0ge FOREIGN KEY (user_id) REFERENCES public.users(id);




