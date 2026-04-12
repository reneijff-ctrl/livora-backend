import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import creatorService from '../api/creatorService';
import SEO from '../components/SEO';
import { showToast } from '../components/Toast';
import Loader from '../components/Loader';
import SelectField from '../components/ui/SelectField';
import CountrySelect from '../components/ui/CountrySelect';
import StateSelect from '../components/ui/StateSelect';
import LanguageSelect from '../components/ui/LanguageSelect';

const GENDER_OPTIONS = [
  { value: 'Male', label: 'Male' },
  { value: 'Female', label: 'Female' },
  { value: 'Trans Male', label: 'Trans Male' },
  { value: 'Trans Female', label: 'Trans Female' },
  { value: 'Non-binary', label: 'Non-binary' },
  { value: 'Prefer not to say', label: 'Prefer not to say' },
];

const CREATOR_TYPE_OPTIONS = [
  { value: 'Solo', label: 'Solo' },
  { value: 'Couple', label: 'Couple' },
  { value: 'Group', label: 'Group' },
];

const INTEREST_OPTIONS = [
  { value: 'Men', label: 'Men' },
  { value: 'Women', label: 'Women' },
  { value: 'Couples', label: 'Couples' },
  { value: 'Trans', label: 'Trans' },
  { value: 'Everyone', label: 'Everyone' },
];

const BODY_TYPE_OPTIONS = [
  { value: 'Slim', label: 'Slim' },
  { value: 'Fit', label: 'Fit' },
  { value: 'Athletic', label: 'Athletic' },
  { value: 'Average', label: 'Average' },
  { value: 'Curvy', label: 'Curvy' },
  { value: 'BBW', label: 'BBW' },
  { value: 'Muscular', label: 'Muscular' },
];

const HAIR_COLOR_OPTIONS = [
  { value: 'Blond', label: 'Blond' },
  { value: 'Brown', label: 'Brown' },
  { value: 'Black', label: 'Black' },
  { value: 'Red', label: 'Red' },
  { value: 'Gray', label: 'Gray' },
  { value: 'Other', label: 'Other' },
];

const EYE_COLOR_OPTIONS = [
  { value: 'Blue', label: 'Blue' },
  { value: 'Green', label: 'Green' },
  { value: 'Brown', label: 'Brown' },
  { value: 'Gray', label: 'Gray' },
  { value: 'Hazel', label: 'Hazel' },
];

const ETHNICITY_OPTIONS = [
  { value: 'White', label: 'White' },
  { value: 'Latina', label: 'Latina' },
  { value: 'Asian', label: 'Asian' },
  { value: 'Black', label: 'Black' },
  { value: 'Middle Eastern', label: 'Middle Eastern' },
  { value: 'Mixed', label: 'Mixed' },
  { value: 'Other', label: 'Other' },
];

const BecomeCreatorPage: React.FC = () => {
  const { user, authLoading, refreshUser } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<{ [key: string]: number }>({});
  const [isDragging, setIsDragging] = useState<{ [key: string]: boolean }>({});
  const [step2Errors, setStep2Errors] = useState<{ realName?: string; birthDate?: string; country?: string; gender?: string }>({});
  
  // Step 1 State
  const [basics, setBasics] = useState({
    username: '',
    displayName: '',
    bio: ''
  });

  // Step 2 State
  const [profile, setProfile] = useState({
    realName: '',
    birthDate: '',
    gender: '',
    creatorType: '',
    interestedIn: '',
    languages: [] as string[],
    location: '',
    state: '',
    bodyType: '',
    heightCm: '',
    weightKg: '',
    ethnicity: '',
    hairColor: '',
    eyeColor: ''
  });

  // Step 3 State
  const [verification, setVerification] = useState({
    governmentIdImage: '',
    selfieWithId: ''
  });

  // Step 4 State
  const [stripeStatus, setStripeStatus] = useState({
    hasAccount: false,
    onboardingCompleted: false
  });

  useEffect(() => {
    if (user) {
      setBasics({
        username: user.username || '',
        displayName: user.displayName || '',
        bio: user.creatorProfile?.bio || ''
      });
      
      if (user.creatorProfile) {
        setProfile({
            realName: (user.creatorProfile as any).realName || '',
            birthDate: (user.creatorProfile as any).birthDate || '',
            gender: (user.creatorProfile as any).gender || '',
            creatorType: (user.creatorProfile as any).creatorType || '',
            interestedIn: (user.creatorProfile as any).interestedIn || '',
            languages: (() => {
              const lang = (user.creatorProfile as any).languages || '';
              if (Array.isArray(lang)) return lang;
              return typeof lang === 'string' && lang ? lang.split(',').map((s: string) => s.trim()).filter(Boolean) : [];
            })(),
            location: (user.creatorProfile as any).location || '',
            state: (user.creatorProfile as any).state || '',
            bodyType: (user.creatorProfile as any).bodyType || '',
            heightCm: (user.creatorProfile as any).heightCm || '',
            weightKg: (user.creatorProfile as any).weightKg || '',
            ethnicity: (user.creatorProfile as any).ethnicity || '',
            hairColor: (user.creatorProfile as any).hairColor || '',
            eyeColor: (user.creatorProfile as any).eyeColor || ''
        });
      }
    }
  }, [user]);

  useEffect(() => {
    if (step === 4) {
        checkStripeStatus();
    }
  }, [step]);

  const checkStripeStatus = async () => {
    try {
        const status = await creatorService.getOnboardingPayoutStatus();
        setStripeStatus({
            hasAccount: !!user?.stripeAccountId,
            onboardingCompleted: status
        });
    } catch (err) {
        console.error('Failed to check stripe status', err);
    }
  };

  const validateStep2 = (): boolean => {
    const errors: { realName?: string; birthDate?: string; country?: string; gender?: string } = {};
    const trimmedName = profile.realName.trim();
    if (!trimmedName) {
      errors.realName = 'Real name is required.';
    } else if (trimmedName.split(/\s+/).filter(Boolean).length < 2) {
      errors.realName = 'Please enter your first and last name.';
    }
    if (!profile.birthDate) {
      errors.birthDate = 'Birth date is required.';
    } else {
      const dob = new Date(profile.birthDate);
      const today = new Date();
      const age = today.getFullYear() - dob.getFullYear() -
        (today < new Date(today.getFullYear(), dob.getMonth(), dob.getDate()) ? 1 : 0);
      if (age < 18) {
        errors.birthDate = 'You must be at least 18 years old.';
      }
    }
    if (!profile.gender) {
      errors.gender = 'Gender is required.';
    }
    if (!profile.location) {
      errors.country = 'Country is required.';
    }
    setStep2Errors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleNext = async () => {
    try {
      setLoading(true);
      if (step === 1) {
        await creatorService.saveOnboardingBasics(basics);
      } else if (step === 2) {
        if (!validateStep2()) { setLoading(false); return; }
        await creatorService.saveOnboardingProfile({
          ...profile,
          languages: profile.languages.join(', '),
        });
      } else if (step === 3) {
        await creatorService.saveOnboardingVerification(verification);
      }
      setStep(step + 1);
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to save progress', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement> | React.DragEvent<HTMLDivElement>, field: string) => {
    let file: File | undefined;
    
    if ('dataTransfer' in e) {
      file = e.dataTransfer.files[0];
    } else if (e.target.files) {
      file = e.target.files[0];
    }

    if (!file) return;

    // Reset progress for this field
    setUploadProgress(prev => ({ ...prev, [field]: 0 }));

    try {
      setIsUploading(true);
      let url = '';
      if (field === 'governmentIdImage') {
        url = await creatorService.uploadVerificationId(file, (p) => {
          setUploadProgress(prev => ({ ...prev, [field]: p }));
        });
      } else if (field === 'selfieWithId') {
        url = await creatorService.uploadVerificationSelfie(file, (p) => {
          setUploadProgress(prev => ({ ...prev, [field]: p }));
        });
      }

      if (step === 3) {
        setVerification({ ...verification, [field]: url });
      }
    } catch (err) {
      showToast('Failed to upload image', 'error');
    } finally {
      setIsUploading(false);
      // Clear progress after a short delay
      setTimeout(() => {
        setUploadProgress(prev => ({ ...prev, [field]: 0 }));
      }, 1000);
    }
  };

  const onDragOver = (e: React.DragEvent<HTMLDivElement>, field: string) => {
    e.preventDefault();
    setIsDragging(prev => ({ ...prev, [field]: true }));
  };

  const onDragLeave = (e: React.DragEvent<HTMLDivElement>, field: string) => {
    e.preventDefault();
    setIsDragging(prev => ({ ...prev, [field]: false }));
  };

  const onDrop = (e: React.DragEvent<HTMLDivElement>, field: string) => {
    e.preventDefault();
    setIsDragging(prev => ({ ...prev, [field]: false }));
    handleImageUpload(e, field);
  };

  const handleConnectStripe = async () => {
    try {
        setLoading(true);
        const { onboardingUrl } = await creatorService.createStripeAccount();
        window.location.href = onboardingUrl;
    } catch (err) {
        showToast('Failed to connect Stripe', 'error');
    } finally {
        setLoading(false);
    }
  };

  const handleFinish = async () => {
      showToast('Onboarding complete! Your account is pending verification.', 'success');
      await refreshUser();
      navigate('/dashboard');
  };

  if (authLoading) return <Loader />;

  return (
    <div className="min-h-screen bg-zinc-950 py-12 px-4 sm:px-6 lg:px-8 text-white">
      <SEO title="Become a Creator" />
      <div className="max-w-3xl mx-auto">
        <div className="text-center mb-10">
          <h1 className="text-4xl font-extrabold tracking-tight mb-2">Become a Creator</h1>
          <p className="text-zinc-400">Complete the steps below to start your journey.</p>
        </div>

        {/* Stepper */}
        <div className="flex items-center justify-between mb-12 px-4">
          {[1, 2, 3, 4].map((s) => (
            <div key={s} className="flex flex-col items-center">
              <div className={`w-10 h-10 rounded-full flex items-center justify-center font-bold border-2 transition-all ${
                step >= s ? 'bg-indigo-600 border-indigo-600' : 'bg-zinc-900 border-zinc-800 text-zinc-600'
              }`}>
                {s}
              </div>
              <span className={`text-xs mt-2 font-medium ${step >= s ? 'text-indigo-400' : 'text-zinc-600'}`}>
                {s === 1 ? 'Basics' : s === 2 ? 'Profile' : s === 3 ? 'Identity' : 'Payout'}
              </span>
            </div>
          ))}
        </div>

        <div className="bg-zinc-900/50 border border-zinc-800 rounded-2xl p-8 shadow-xl backdrop-blur-sm">
          {step === 1 && (
            <div className="space-y-6">
              <h2 className="text-2xl font-bold mb-6">Account Basics</h2>
              <div className="flex items-center gap-3 p-4 mb-4 rounded-xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 text-sm">
                📸 You can upload your profile picture after becoming a creator.
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-zinc-400 mb-2">Username</label>
                  <input 
                    type="text" 
                    value={basics.username} 
                    onChange={e => setBasics({...basics, username: e.target.value})}
                    className="w-full bg-zinc-800 border border-zinc-700 rounded-xl px-4 py-3 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    placeholder="e.g. jdoe"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-zinc-400 mb-2">Display Name</label>
                  <input 
                    type="text" 
                    value={basics.displayName} 
                    onChange={e => setBasics({...basics, displayName: e.target.value})}
                    className="w-full bg-zinc-800 border border-zinc-700 rounded-xl px-4 py-3 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    placeholder="e.g. John Doe"
                  />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-zinc-400 mb-2">Bio</label>
                <textarea 
                  value={basics.bio} 
                  onChange={e => setBasics({...basics, bio: e.target.value})}
                  className="w-full bg-zinc-800 border border-zinc-700 rounded-xl px-4 py-3 focus:outline-none focus:ring-2 focus:ring-indigo-500 min-h-[120px]"
                  placeholder="Tell us about yourself..."
                />
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-6">
              <h2 className="text-2xl font-bold mb-6">Creator Profile</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-zinc-400 mb-2">Real Name</label>
                  <input type="text" value={profile.realName} onChange={e => { setProfile({...profile, realName: e.target.value}); setStep2Errors(prev => ({...prev, realName: undefined})); }} className={`w-full bg-zinc-800 border rounded-xl px-4 py-3 ${step2Errors.realName ? 'border-red-500 focus:ring-red-500' : 'border-zinc-700 focus:ring-indigo-500'} focus:outline-none focus:ring-2`} placeholder="e.g. Jane Doe" />
                  {step2Errors.realName && <p className="mt-1 text-xs text-red-400">{step2Errors.realName}</p>}
                </div>
                <div>
                  <label className="block text-sm font-medium text-zinc-400 mb-2">Birth Date</label>
                  <input type="date" value={profile.birthDate} onChange={e => { setProfile({...profile, birthDate: e.target.value}); setStep2Errors(prev => ({...prev, birthDate: undefined})); }} className={`w-full bg-zinc-800 border rounded-xl px-4 py-3 ${step2Errors.birthDate ? 'border-red-500 focus:ring-red-500' : 'border-zinc-700 focus:ring-indigo-500'} focus:outline-none focus:ring-2`} />
                  {step2Errors.birthDate && <p className="mt-1 text-xs text-red-400">{step2Errors.birthDate}</p>}
                </div>
                <div>
                  <SelectField
                    label="Gender *"
                    value={profile.gender}
                    onChange={val => { setProfile({...profile, gender: val}); setStep2Errors(prev => ({...prev, gender: undefined})); }}
                    options={GENDER_OPTIONS}
                    placeholder="Select gender"
                  />
                  {step2Errors.gender && <p className="mt-1 text-xs text-red-400">{step2Errors.gender}</p>}
                </div>
                <SelectField
                  label="Creator Type"
                  value={profile.creatorType}
                  onChange={val => setProfile({...profile, creatorType: val})}
                  options={CREATOR_TYPE_OPTIONS}
                  placeholder="Select creator type"
                />
                <SelectField
                  label="Interested In"
                  value={profile.interestedIn}
                  onChange={val => setProfile({...profile, interestedIn: val})}
                  options={INTEREST_OPTIONS}
                  placeholder="Select interest"
                />
                <div>
                  <label className="block text-sm font-medium text-zinc-400 mb-2">Languages</label>
                  <LanguageSelect
                    value={profile.languages}
                    onChange={(codes) => setProfile({...profile, languages: codes})}
                    placeholder="Select languages"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-zinc-400 mb-2">Country</label>
                  <CountrySelect
                    value={profile.location}
                    onChange={(code) => { setProfile({...profile, location: code, state: ''}); setStep2Errors(prev => ({...prev, country: undefined})); }}
                    placeholder="Select country"
                  />
                  {step2Errors.country && <p className="mt-1 text-xs text-red-400">{step2Errors.country}</p>}
                </div>
                <div>
                  <label className="block text-sm font-medium text-zinc-400 mb-2">State / Province</label>
                  <StateSelect
                    countryCode={profile.location}
                    value={profile.state}
                    onChange={(val) => setProfile({...profile, state: val})}
                    placeholder="Select state/province"
                  />
                </div>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <SelectField
                  label="Body Type"
                  value={profile.bodyType}
                  onChange={val => setProfile({...profile, bodyType: val})}
                  options={BODY_TYPE_OPTIONS}
                  placeholder="Select body type"
                />
                <div>
                  <label className="block text-sm font-medium text-zinc-400 mb-2">Height (cm)</label>
                  <input type="number" value={profile.heightCm} onChange={e => setProfile({...profile, heightCm: e.target.value})} className="w-full bg-zinc-800 border border-zinc-700 rounded-xl px-4 py-3" placeholder="175" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-zinc-400 mb-2">Weight (kg)</label>
                  <input type="number" value={profile.weightKg} onChange={e => setProfile({...profile, weightKg: e.target.value})} className="w-full bg-zinc-800 border border-zinc-700 rounded-xl px-4 py-3" placeholder="70" />
                </div>
                <SelectField
                  label="Ethnicity"
                  value={profile.ethnicity}
                  onChange={val => setProfile({...profile, ethnicity: val})}
                  options={ETHNICITY_OPTIONS}
                  placeholder="Select ethnicity"
                />
                <SelectField
                  label="Hair Color"
                  value={profile.hairColor}
                  onChange={val => setProfile({...profile, hairColor: val})}
                  options={HAIR_COLOR_OPTIONS}
                  placeholder="Select hair color"
                />
                <SelectField
                  label="Eye Color"
                  value={profile.eyeColor}
                  onChange={val => setProfile({...profile, eyeColor: val})}
                  options={EYE_COLOR_OPTIONS}
                  placeholder="Select eye color"
                />
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="space-y-8">
              <h2 className="text-2xl font-bold mb-6">ID Verification</h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                <div className="flex flex-col">
                  <label className="block text-sm font-medium text-zinc-400 mb-4 text-center">Government ID Image</label>
                  <div 
                    onDragOver={(e) => onDragOver(e, 'governmentIdImage')}
                    onDragLeave={(e) => onDragLeave(e, 'governmentIdImage')}
                    onDrop={(e) => onDrop(e, 'governmentIdImage')}
                    className={`aspect-[3/2] bg-zinc-800 rounded-2xl border-2 border-dashed flex flex-col items-center justify-center overflow-hidden relative group transition-all ${
                      isDragging['governmentIdImage'] ? 'border-indigo-500 bg-indigo-500/5' : 'border-zinc-700'
                    }`}
                  >
                    {verification.governmentIdImage ? (
                        <img src={verification.governmentIdImage} alt="ID" className="w-full h-full object-cover" />
                    ) : (
                        <div className="text-center p-4">
                            <span className="text-4xl mb-2 block">🪪</span>
                            <p className="text-xs text-zinc-500">Drag & drop or click to upload ID front</p>
                        </div>
                    )}
                    {uploadProgress['governmentIdImage'] > 0 && (
                      <div className="absolute bottom-0 left-0 right-0 h-1 bg-zinc-700">
                        <div 
                          className="h-full bg-indigo-500 transition-all duration-300" 
                          style={{ width: `${uploadProgress['governmentIdImage']}%` }}
                        />
                      </div>
                    )}
                    <input type="file" id="id-front" className="absolute inset-0 opacity-0 cursor-pointer" onChange={(e) => handleImageUpload(e, 'governmentIdImage')} />
                  </div>
                </div>
                <div className="flex flex-col">
                  <label className="block text-sm font-medium text-zinc-400 mb-4 text-center">Selfie with ID</label>
                  <div 
                    onDragOver={(e) => onDragOver(e, 'selfieWithId')}
                    onDragLeave={(e) => onDragLeave(e, 'selfieWithId')}
                    onDrop={(e) => onDrop(e, 'selfieWithId')}
                    className={`aspect-[3/2] bg-zinc-800 rounded-2xl border-2 border-dashed flex flex-col items-center justify-center overflow-hidden relative group transition-all ${
                      isDragging['selfieWithId'] ? 'border-indigo-500 bg-indigo-500/5' : 'border-zinc-700'
                    }`}
                  >
                    {verification.selfieWithId ? (
                        <img src={verification.selfieWithId} alt="Selfie" className="w-full h-full object-cover" />
                    ) : (
                        <div className="text-center p-4">
                            <span className="text-4xl mb-2 block">🤳</span>
                            <p className="text-xs text-zinc-500">Drag & drop or click to upload selfie</p>
                        </div>
                    )}
                    {uploadProgress['selfieWithId'] > 0 && (
                      <div className="absolute bottom-0 left-0 right-0 h-1 bg-zinc-700">
                        <div 
                          className="h-full bg-indigo-500 transition-all duration-300" 
                          style={{ width: `${uploadProgress['selfieWithId']}%` }}
                        />
                      </div>
                    )}
                    <input type="file" id="selfie" className="absolute inset-0 opacity-0 cursor-pointer" onChange={(e) => handleImageUpload(e, 'selfieWithId')} />
                  </div>
                </div>
              </div>
            </div>
          )}

          {step === 4 && (
            <div className="space-y-8 text-center py-8">
              <h2 className="text-2xl font-bold mb-6">Payout Setup</h2>
              <div className="mb-8">
                  <div className="w-20 h-20 bg-indigo-600/10 rounded-full flex items-center justify-center mx-auto mb-4">
                    <span className="text-3xl">💰</span>
                  </div>
                  <p className="text-zinc-400 max-w-sm mx-auto">
                    We use Stripe to handle secure payouts. Connect your account to start receiving earnings.
                  </p>
              </div>
              
              {!stripeStatus.onboardingCompleted ? (
                  <button 
                    onClick={handleConnectStripe}
                    disabled={loading}
                    className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold py-4 px-8 rounded-2xl transition-all shadow-lg shadow-indigo-600/20 w-full max-w-xs"
                  >
                    {loading ? 'Redirecting...' : 'Connect Stripe'}
                  </button>
              ) : (
                  <div className="bg-green-500/10 border border-green-500/20 text-green-400 p-6 rounded-2xl flex items-center justify-center gap-3">
                      <span>✅</span>
                      <span className="font-bold">Stripe account connected and verified!</span>
                  </div>
              )}
            </div>
          )}

          <div className="mt-12 flex items-center justify-between border-t border-zinc-800 pt-8">
            {step > 1 && (
              <button onClick={() => setStep(step - 1)} className="text-zinc-400 hover:text-white font-semibold flex items-center gap-2">
                ← Back
              </button>
            )}
            <div className="ml-auto">
                {step < 4 ? (
                    <button 
                        onClick={handleNext} 
                        disabled={loading || isUploading || (step === 3 && (!verification.governmentIdImage || !verification.selfieWithId))}
                        className="bg-indigo-600 hover:bg-indigo-500 disabled:bg-zinc-800 disabled:text-zinc-500 text-white font-bold py-3 px-10 rounded-xl transition-all shadow-lg shadow-indigo-600/20"
                    >
                        {loading ? 'Saving...' : 'Next Step →'}
                    </button>
                ) : (
                    <button 
                        onClick={handleFinish}
                        disabled={!stripeStatus.onboardingCompleted}
                        className="bg-green-600 hover:bg-green-500 disabled:bg-zinc-800 disabled:text-zinc-500 text-white font-bold py-3 px-10 rounded-xl transition-all shadow-lg shadow-green-600/20"
                    >
                        Finish Onboarding
                    </button>
                )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BecomeCreatorPage;
