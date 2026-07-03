import { BrowserRouter, Routes, Route } from "react-router-dom";
import RoadmapChat from "./pages/RoadmapChat";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<RoadmapChat />} />
      </Routes>
    </BrowserRouter>
  );
}
