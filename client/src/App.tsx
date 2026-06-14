import { BrowserRouter, Routes, Route } from "react-router-dom";
import AdminPanel from "./pages/AdminPanel";
import RoadmapChat from "./pages/RoadmapChat";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<RoadmapChat />} />
        <Route path="/admin" element={<AdminPanel />} />
      </Routes>
    </BrowserRouter>
  );
}
