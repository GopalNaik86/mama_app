import React, { useState, useEffect, useRef } from 'react';
import { initializeApp } from 'firebase/app';
import { getAuth, signInAnonymously, onAuthStateChanged, signInWithCustomToken } from 'firebase/auth';
import { getFirestore, collection, addDoc, query, onSnapshot, serverTimestamp } from 'firebase/firestore';
import { Camera, Shield, X, Check, Search, User, Navigation as NavIcon, ArrowUpDown, Radio, Clock, AlertTriangle, Loader2, MapPin } from 'lucide-react';

// --- Configuration ---
const firebaseConfig = JSON.parse(typeof __firebase_config !== 'undefined' ? __firebase_config : '{}');
// Use your Key
const GOOGLE_MAPS_API_KEY = "AIzaSyCwNrhx7F01mLhSFTEAlKnSgNLB_aJskR4"; 

const app = initializeApp(Object.keys(firebaseConfig).length > 0 ? firebaseConfig : { apiKey: "placeholder", projectId: "placeholder" });
const auth = getAuth(app);
const db = getFirestore(app);

// --- Constants ---
const DUMMY_REPORTS = [
  { id: 'd4', lat: 12.9177, lng: 77.6238, verifiedCount: 189 }, // Silk Board
  { id: 'd6', lat: 12.9352, lng: 77.6245, verifiedCount: 67 }, // Koramangala
];

// --- Helpers ---
const loadGoogleMapsScript = (apiKey) => {
  return new Promise((resolve) => {
    if (window.google && window.google.maps) { resolve(window.google.maps); return; }
    if (document.querySelector(`script[src*="maps.googleapis.com"]`)) {
       const i = setInterval(() => { if(window.google) { clearInterval(i); resolve(window.google.maps); } }, 100);
       return;
    }
    const script = document.createElement('script');
    script.src = `https://maps.googleapis.com/maps/api/js?key=${apiKey}&libraries=places,geometry,drawing`;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve(window.google.maps);
    document.head.appendChild(script);
  });
};

// --- Components ---

const GooglePlacesInput = ({ placeholder, value, onChange, onSelect, icon: Icon }) => {
  const inputRef = useRef(null);
  const autocompleteRef = useRef(null);

  useEffect(() => {
    if (inputRef.current && !autocompleteRef.current && window.google) {
      const options = {
        componentRestrictions: { country: "in" },
        fields: ["geometry", "name", "formatted_address"],
        strictBounds: false,
      };
      // Bias to Bengaluru
      options.bounds = new window.google.maps.LatLngBounds(
          new window.google.maps.LatLng(12.80, 77.35),
          new window.google.maps.LatLng(13.15, 77.85)
      );

      autocompleteRef.current = new window.google.maps.places.Autocomplete(inputRef.current, options);
      autocompleteRef.current.addListener("place_changed", () => {
        const place = autocompleteRef.current.getPlace();
        if (place.geometry) {
          onChange(place.name);
          onSelect({ 
            lat: place.geometry.location.lat(), 
            lng: place.geometry.location.lng(),
            name: place.name 
          });
        }
      });
    }
  }, []);

  return (
    <div className="relative flex-1 group">
       <div className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500 z-10 pointer-events-none">
          {Icon ? <Icon size={18} /> : <Search size={18} />}
       </div>
       <input 
          ref={inputRef}
          type="text" 
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          className="w-full bg-white text-gray-900 pl-10 pr-4 py-3 rounded-xl text-sm font-medium shadow-sm border border-gray-200 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow"
       />
       {value && (
           <button onClick={() => onChange('')} className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 p-1">
               <X size={14} />
           </button>
       )}
    </div>
  );
};

const GoogleMapComponent = ({ center, source, dest, reports, onRouteInfo }) => {
  const mapRef = useRef(null);
  const mapObj = useRef(null);
  const dirService = useRef(null);
  const dirRender = useRef(null);
  const markers = useRef([]);

  useEffect(() => {
    if (window.google && !mapObj.current) {
        mapObj.current = new window.google.maps.Map(mapRef.current, {
            center: center,
            zoom: 14,
            disableDefaultUI: true,
            zoomControl: false,
            styles: [ { featureType: "poi", elementType: "labels.icon", stylers: [{ visibility: "on" }] } ]
        });
        dirService.current = new window.google.maps.DirectionsService();
        dirRender.current = new window.google.maps.DirectionsRenderer({
            map: mapObj.current,
            suppressMarkers: false,
            polylineOptions: { strokeColor: "#2563eb", strokeWeight: 5 }
        });
    }
  }, []);

  // Sync View & Markers
  useEffect(() => {
      if(!mapObj.current) return;
      if (!dest) mapObj.current.panTo(center);

      // Clear old
      markers.current.forEach(m => m.setMap(null));
      markers.current = [];

      // Add Mamas
      reports.forEach(r => {
          const m = new window.google.maps.Marker({
              position: r,
              map: mapObj.current,
              icon: "https://maps.google.com/mapfiles/ms/icons/police.png",
              animation: window.google.maps.Animation.DROP
          });
          markers.current.push(m);
      });

      // Add Route Points
      if(source && dest) {
         const sM = new window.google.maps.Marker({ position: source, map: mapObj.current, icon: { path: window.google.maps.SymbolPath.CIRCLE, scale: 7, fillColor: "#2563eb", fillOpacity: 1, strokeColor: "white", strokeWeight: 2 } });
         const dM = new window.google.maps.Marker({ position: dest, map: mapObj.current, icon: { path: window.google.maps.SymbolPath.CIRCLE, scale: 7, fillColor: "#ef4444", fillOpacity: 1, strokeColor: "white", strokeWeight: 2 } });
         markers.current.push(sM, dM);
      }
  }, [center, reports, source, dest]);

  // Routing
  useEffect(() => {
      if (source && dest && dirService.current) {
          dirService.current.route({
              origin: source.lat ? source : source.name,
              destination: dest.lat ? dest : dest.name,
              travelMode: 'DRIVING'
          }, (res, status) => {
              if (status === 'OK') {
                  dirRender.current.setDirections(res);
                  const leg = res.routes[0].legs[0];
                  onRouteInfo({ distance: leg.distance.text, duration: leg.duration.text });
              }
          });
      }
  }, [source, dest]);

  return <div ref={mapRef} className="w-full h-full" />;
};

export default function App() {
  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState(null);
  const [currentLoc, setCurrentLoc] = useState({ lat: 12.9716, lng: 77.5946 });
  
  const [source, setSource] = useState(null);
  const [dest, setDest] = useState(null);
  const [sourceQuery, setSourceQuery] = useState('Current Location');
  const [destQuery, setDestQuery] = useState('');
  const [routeInfo, setRouteInfo] = useState(null);

  useEffect(() => {
      loadGoogleMapsScript(GOOGLE_MAPS_API_KEY).then(() => setLoading(false));
      const initAuth = async () => { 
        try {
            if (typeof __initial_auth_token !== 'undefined' && __initial_auth_token) await signInWithCustomToken(auth, __initial_auth_token); 
            else await signInAnonymously(auth);
        } catch(e) { console.error(e); }
      };
      initAuth();
      navigator.geolocation.getCurrentPosition(
          p => {
              const loc = { lat: p.coords.latitude, lng: p.coords.longitude };
              setCurrentLoc(loc);
              setSource(loc); // Default start is GPS
          },
          () => console.warn("GPS denied")
      );
  }, []);

  const swapLoc = () => {
      const tQ = sourceQuery; const tL = source;
      setSourceQuery(destQuery); setSource(dest);
      setDestQuery(tQ); setDest(tL);
  };

  if (loading) return <div className="h-screen w-full flex items-center justify-center bg-gray-50"><Loader2 className="animate-spin text-blue-600" /></div>;

  return (
    <div className="h-screen w-full flex flex-col bg-white relative font-sans">
      
      {/* --- CLEAN HEADER & SEARCH (Matches 2nd Screenshot) --- */}
      <div className="absolute top-0 left-0 right-0 p-4 z-20 flex flex-col gap-3 pointer-events-none">
        
        {/* Top Bar: Clean White Pill */}
        <div className="flex justify-between items-center pointer-events-auto">
            <div className="bg-white shadow-md px-4 py-2 rounded-full flex items-center gap-2 border border-gray-100">
                <Shield className="text-black fill-current" size={18} />
                <span className="text-black font-bold text-sm tracking-tight">MAMA MAPS</span>
            </div>
            
            {/* Profile Pill */}
            <div className="flex gap-2">
                <button className="bg-white p-2 rounded-full shadow-md border border-gray-100 text-gray-600"><Radio size={18} /></button>
                <div className="bg-black text-white px-4 py-2 rounded-full text-xs font-bold flex items-center gap-2 shadow-md">
                    <User size={14} /> Karthik
                </div>
            </div>
        </div>

        {/* Search Container */}
        <div className="bg-white p-1.5 rounded-2xl shadow-xl border border-gray-100 pointer-events-auto flex flex-col gap-1">
            <div className="flex gap-2 items-center">
                <GooglePlacesInput 
                    placeholder="Start Location" 
                    value={sourceQuery} 
                    onChange={setSourceQuery} 
                    onSelect={(p) => { setSourceQuery(p.name); setSource(p); }} 
                    icon={NavIcon} 
                />
                <button onClick={swapLoc} className="bg-gray-50 p-2 rounded-full text-gray-500 hover:bg-gray-100"><ArrowUpDown size={16} /></button>
            </div>
            {/* Divider */}
            <div className="h-[1px] bg-gray-100 mx-2" />
            <GooglePlacesInput 
                placeholder="Where to?" 
                value={destQuery} 
                onChange={setDestQuery} 
                onSelect={(p) => { setDestQuery(p.name); setDest(p); }} 
                icon={Search} 
            />
        </div>
      </div>

      {/* Map */}
      <div className="flex-1 relative z-0">
        <GoogleMapComponent center={currentLoc} source={source} dest={dest} reports={DUMMY_REPORTS} onRouteInfo={setRouteInfo} />
        
        {/* Route Info Card */}
        {routeInfo && (
            <div className="absolute bottom-32 left-4 right-4 bg-white p-4 rounded-xl shadow-[0_8px_30px_rgb(0,0,0,0.12)] border border-gray-100 z-20 flex justify-between items-center animate-in slide-in-from-bottom">
                <div>
                    <p className="text-xs text-green-600 font-bold uppercase mb-0.5">Fastest Route</p>
                    <p className="text-2xl font-black text-gray-900">{routeInfo.duration}</p>
                </div>
                <div className="text-right">
                    <p className="text-xl font-bold text-blue-600">{routeInfo.distance}</p>
                </div>
            </div>
        )}

        {/* FAB */}
        <div className="absolute bottom-8 left-0 w-full flex justify-center z-20 pointer-events-none">
            <button className="pointer-events-auto w-16 h-16 bg-yellow-400 rounded-full border-4 border-white shadow-2xl flex items-center justify-center hover:scale-105 transition-transform text-black">
                <Camera size={28} strokeWidth={2.5} />
            </button>
        </div>
      </div>
    </div>
  );
}